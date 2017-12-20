/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

/** A mutable set interface that uses object identity to test for set membership.*/
trait IDSet[T] {
  def apply(t: T): Boolean
  def contains(t: T): Boolean
  def +=(t: T): Unit
  def ++=(t: Iterable[T]): Unit
  def -=(t: T): Boolean
  def all: collection.Iterable[T]
  def toList: List[T]
  def isEmpty: Boolean
  def foreach(f: T => Unit): Unit
  def process[S](t: T)(ifSeen: S)(ifNew: => S): S
}

object IDSet {
  implicit def toTraversable[T]: IDSet[T] => Traversable[T] = _.all
  def apply[T](values: T*): IDSet[T] = apply(values)

  def apply[T](values: Iterable[T]): IDSet[T] = {
    val s = create[T]
    s ++= values
    s
  }

  def create[T]: IDSet[T] = new IDSet[T] {
    private[this] val backing = new java.util.IdentityHashMap[T, AnyRef]
    private[this] val Dummy: AnyRef = ""

    def apply(t: T) = contains(t)
    def contains(t: T) = backing.containsKey(t)
    def foreach(f: T => Unit) = all foreach f
    def +=(t: T) = { backing.put(t, Dummy); () }
    def ++=(t: Iterable[T]) = t foreach +=
    def -=(t: T) = if (backing.remove(t) eq null) false else true
    def all = collection.JavaConverters.collectionAsScalaIterable(backing.keySet)
    def toList = all.toList
    def isEmpty = backing.isEmpty

    def process[S](t: T)(ifSeen: S)(ifNew: => S) =
      if (contains(t)) ifSeen else { this += t; ifNew }

    override def toString = backing.toString
  }
}
