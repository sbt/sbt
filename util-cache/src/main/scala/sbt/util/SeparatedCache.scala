/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import scala.util.Try

import sjsonnew.JsonFormat
import sjsonnew.support.murmurhash.Hasher

import CacheImplicits._

/**
 * A cache that stores a single value.
 */
trait SingletonCache[A] {

  /** Reads the cache from the backing `from`. */
  def read(from: Input): A

  /** Writes `value` to the backing `to`. */
  def write(to: Output, value: A): Unit

}

object SingletonCache {

  given basicSingletonCache[A: JsonFormat]: SingletonCache[A] =
    new SingletonCache[A] {
      override def read(from: Input): A = from.read[A]()
      override def write(to: Output, value: A) = to.write(value)
    }

  /** A lazy `SingletonCache` */
  def lzy[A: JsonFormat](mkCache: => SingletonCache[A]): SingletonCache[A] =
    new SingletonCache[A] {
      lazy val cache = mkCache
      override def read(from: Input): A = cache.read(from)
      override def write(to: Output, value: A) = cache.write(to, value)
    }
}

/**
 * Simple key-value cache.
 */
class BasicCache[I: JsonFormat, O: JsonFormat] extends Cache[I, O] {
  private val singletonCache: SingletonCache[(Long, O)] = implicitly
  val jsonFormat: JsonFormat[I] = implicitly
  override def apply(store: CacheStore)(key: I): CacheResult[O] = {
    val keyHash: Long = Hasher.hashUnsafe[I](key).toLong
    Try {
      val (previousKeyHash, previousValue) = singletonCache.read(store)
      if (keyHash == previousKeyHash) Hit(previousValue)
      else Miss(update(store)(keyHash))
    } getOrElse Miss(update(store)(keyHash))
  }

  private def update(store: CacheStore)(keyHash: Long) = (value: O) => {
    singletonCache.write(store, (keyHash, value))
  }
}
