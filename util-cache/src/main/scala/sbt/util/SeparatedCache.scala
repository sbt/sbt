/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.util

import scala.util.Try

import sjsonnew.JsonFormat

import CacheImplicits._

/**
 * A cache that stores a single value.
 */
trait SingletonCache[T] {
  /** Reads the cache from the backing `from`. */
  def read(from: Input): T

  /** Writes `value` to the backing `to`. */
  def write(to: Output, value: T): Unit

  /** Equivalence for elements of type `T`. */
  def equiv: Equiv[T]
}

object SingletonCache {

  implicit def basicSingletonCache[T: JsonFormat: Equiv]: SingletonCache[T] =
    new SingletonCache[T] {
      override def read(from: Input): T = from.read[T]
      override def write(to: Output, value: T) = to.write(value)
      override def equiv: Equiv[T] = implicitly
    }

  /** A lazy `SingletonCache` */
  def lzy[T: JsonFormat: Equiv](mkCache: => SingletonCache[T]): SingletonCache[T] =
    new SingletonCache[T] {
      lazy val cache = mkCache
      override def read(from: Input): T = cache.read(from)
      override def write(to: Output, value: T) = cache.write(to, value)
      override def equiv = cache.equiv
    }
}

/**
 * Simple key-value cache.
 */
class BasicCache[I: JsonFormat: Equiv, O: JsonFormat] extends Cache[I, O] {
  private val singletonCache: SingletonCache[(I, O)] = implicitly
  val equiv: Equiv[I] = implicitly
  override def apply(store: CacheStore)(key: I): CacheResult[O] =
    Try {
      val (previousKey, previousValue) = singletonCache.read(store)
      if (equiv.equiv(key, previousKey))
        Hit(previousValue)
      else
        Miss(update(store)(key))
    } getOrElse Miss(update(store)(key))

  private def update(store: CacheStore)(key: I) = (value: O) => {
    singletonCache.write(store, (key, value))
  }
}
