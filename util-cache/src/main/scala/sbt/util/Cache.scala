/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.File

/** The result of a cache query */
sealed trait CacheResult[K]

/** A successful hit on the cache */
case class Hit[O](value: O) extends CacheResult[O]

/**
 * A cache miss. `update` associates the missing key with `O` in the cache.
 */
case class Miss[O](update: O => Unit) extends CacheResult[O]

/**
 * A simple cache with keys of type `I` and values of type `O`
 */
trait Cache[I, O] {

  /**
   * Queries the cache backed with store `store` for key `key`.
   */
  def apply(store: CacheStore)(key: I): CacheResult[O]
}

object Cache {

  /**
   * Materializes a cache.
   */
  def cache[I, O](using c: Cache[I, O]): Cache[I, O] = c

  /**
   * Returns a function that represents a cache that inserts on miss.
   *
   * @param cacheFile
   *   The store that backs this cache.
   * @param default
   *   A function that computes a default value to insert on
   */
  def cached[I, O](cacheFile: File)(default: I => O)(using cache: Cache[I, O]): I => O =
    cached(CacheStore(cacheFile))(default)

  /**
   * Returns a function that represents a cache that inserts on miss.
   *
   * @param store
   *   The store that backs this cache.
   * @param default
   *   A function that computes a default value to insert on
   */
  def cached[I, O](store: CacheStore)(default: I => O)(using cache: Cache[I, O]): I => O =
    key =>
      cache(store)(key) match {
        case Hit(value) =>
          value

        case Miss(update) =>
          val result = default(key)
          update(result)
          result
      }

  def debug[I](label: String, cache: SingletonCache[I]): SingletonCache[I] =
    new SingletonCache[I] {
      override def read(from: Input): I = {
        val value = cache.read(from)
        println(label + ".read: " + value)
        value
      }

      override def write(to: Output, value: I): Unit = {
        println(label + ".write: " + value)
        cache.write(to, value)
      }
    }
}
