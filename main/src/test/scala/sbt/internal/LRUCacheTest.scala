/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.{ FlatSpec, Matchers }

class LRUCacheTest extends FlatSpec with Matchers {
  "LRUCache" should "flush entries when full" in {
    val cache = LRUCache[Int, Int](2)
    cache.put(1, 1)
    cache.put(2, 2)
    cache.put(3, 3)
    assert(cache.get(1).isEmpty)
    assert(cache.get(2).contains(2))
    assert(cache.get(3).contains(3))

    assert(cache.get(2).contains(2))
    cache.put(1, 1)
    assert(cache.get(3).isEmpty)
    assert(cache.get(2).contains(2))
    assert(cache.get(1).contains(1))
  }
  it should "remove entries" in {
    val cache = LRUCache[Int, Int](2)
    cache.put(1, 1)
    cache.put(2, 2)
    assert(cache.get(1).contains(1))
    assert(cache.get(2).contains(2))

    assert(cache.remove(1).getOrElse(-1) == 1)
    assert(cache.get(1).isEmpty)
    assert(cache.get(2).contains(2))
  }
  it should "clear entries on close" in {
    val cache = LRUCache[Int, Int](2)
    cache.put(1, 1)
    assert(cache.get(1).contains(1))
    cache.close()
    assert(cache.get(1).isEmpty)
  }
  it should "call onExpire in close" in {
    val count = new AtomicInteger(0)
    val cache =
      LRUCache[Int, Int](
        maxSize = 3,
        onExpire = (_: Int, _: Int) => { count.getAndIncrement(); () }
      )
    cache.put(1, 1)
    cache.put(2, 2)
    cache.put(3, 3)
    cache.put(4, 4)
    assert(count.get == 1)
    cache.close()
    assert(count.get == 4)
  }
  it should "apply on remove function" in {
    val value = new AtomicInteger(0)
    val cache = LRUCache[Int, Int](1, (k: Int, v: Int) => value.set(k + v))
    cache.put(1, 3)
    cache.put(2, 2)
    assert(value.get() == 4)
    assert(cache.get(2).contains(2))
  }
  it should "print sorted entries in toString" in {
    val cache = LRUCache[Int, Int](2)
    cache.put(2, 2)
    cache.put(1, 1)
    assert(cache.toString == s"LRUCache(2 -> 2, 1 -> 1)")
  }
}
