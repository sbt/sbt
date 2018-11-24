/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.tailrec

private[sbt] sealed trait LRUCache[K, V] extends AutoCloseable {
  def get(key: K): Option[V]
  def entries: Seq[(K, V)]
  def maxSize: Int
  def put(key: K, value: V): Option[V]
  def remove(key: K): Option[V]
  def size: Int
}

private[sbt] object LRUCache {
  private[this] class impl[K, V](override val maxSize: Int, onExpire: Option[((K, V)) => Unit])
      extends LRUCache[K, V] {
    private[this] val elementsSortedByAccess: Array[(K, V)] = new Array[(K, V)](maxSize)
    private[this] val lastIndex: AtomicInteger = new AtomicInteger(-1)

    override def close(): Unit = this.synchronized {
      val f = onExpire.getOrElse((_: (K, V)) => Unit)
      0 until maxSize foreach { i =>
        elementsSortedByAccess(i) match {
          case null =>
          case el   => f(el)
        }
        elementsSortedByAccess(i) = null
      }
      lastIndex.set(-1)
    }
    override def entries: Seq[(K, V)] = this.synchronized {
      (0 to lastIndex.get()).map(elementsSortedByAccess)
    }
    override def get(key: K): Option[V] = this.synchronized {
      indexOf(key) match {
        case -1 => None
        case i  => replace(i, key, elementsSortedByAccess(i)._2)
      }
    }
    override def put(key: K, value: V): Option[V] = this.synchronized {
      indexOf(key) match {
        case -1 =>
          append(key, value)
          None
        case i => replace(i, key, value)
      }
    }
    override def remove(key: K): Option[V] = this.synchronized {
      indexOf(key) match {
        case -1 => None
        case i  => remove(i, lastIndex.get, expire = false)
      }
    }
    override def size: Int = lastIndex.get + 1
    override def toString: String = {
      val values = 0 to lastIndex.get() map { i =>
        val (key, value) = elementsSortedByAccess(i)
        s"$key -> $value"
      }
      s"LRUCache(${values mkString ", "})"
    }

    private def indexOf(key: K): Int =
      elementsSortedByAccess.view.take(lastIndex.get() + 1).indexWhere(_._1 == key)
    private def replace(index: Int, key: K, value: V): Option[V] = {
      val prev = remove(index, lastIndex.get(), expire = false)
      append(key, value)
      prev
    }
    private def append(key: K, value: V): Unit = {
      while (lastIndex.get() >= maxSize - 1) {
        remove(0, lastIndex.get(), expire = true)
      }
      val index = lastIndex.incrementAndGet()
      elementsSortedByAccess(index) = (key, value)
    }
    private def remove(index: Int, endIndex: Int, expire: Boolean): Option[V] = {
      @tailrec
      def shift(i: Int): Unit = if (i < endIndex) {
        elementsSortedByAccess(i) = elementsSortedByAccess(i + 1)
        shift(i + 1)
      }
      val prev = elementsSortedByAccess(index)
      shift(index)
      lastIndex.set(endIndex - 1)
      if (expire) onExpire.foreach(f => f(prev))
      Some(prev._2)
    }
  }
  private def emptyCache[K, V]: LRUCache[K, V] = new LRUCache[K, V] {
    override def get(key: K): Option[V] = None
    override def entries: Seq[(K, V)] = Nil
    override def maxSize: Int = 0
    override def put(key: K, value: V): Option[V] = None
    override def remove(key: K): Option[V] = None
    override def size: Int = 0
    override def close(): Unit = {}
    override def toString = "EmptyLRUCache"
  }
  def apply[K, V](maxSize: Int): LRUCache[K, V] =
    if (maxSize > 0) new impl(maxSize, None) else emptyCache
  def apply[K, V](maxSize: Int, onExpire: (K, V) => Unit): LRUCache[K, V] =
    if (maxSize > 0) new impl(maxSize, Some(onExpire.tupled)) else emptyCache
}
