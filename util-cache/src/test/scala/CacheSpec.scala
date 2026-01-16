/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sbt.io.IO
import sbt.io.syntax.*
import verify.BasicTestSuite

import CacheImplicits.given

object CacheSpec extends BasicTestSuite:

  test("A cache should NOT throw an exception if read without being written previously"):
    testCache[String, Int] { (cache, store) =>
      cache(store)("missing") match
        case Hit(_)  => assert(false, "Expected Miss but got Hit")
        case Miss(_) => ()
    }

  test("A cache should write a very simple value"):
    testCache[String, Int] { (cache, store) =>
      cache(store)("missing") match
        case Hit(_)       => assert(false, "Expected Miss but got Hit")
        case Miss(update) => update(5)
    }

  test("A cache should be updatable"):
    testCache[String, Int] { (cache, store) =>
      val value = 5
      cache(store)("someKey") match
        case Hit(_)       => assert(false, "Expected Miss but got Hit")
        case Miss(update) => update(value)

      cache(store)("someKey") match
        case Hit(read) => assert(read == value)
        case Miss(_)   => assert(false, "Expected Hit but got Miss")
    }

  test("A cache should return the value that has been previously written"):
    testCache[String, Int] { (cache, store) =>
      val key = "someKey"
      val value = 5
      cache(store)(key) match
        case Hit(_)       => assert(false, "Expected Miss but got Hit")
        case Miss(update) => update(value)

      cache(store)(key) match
        case Hit(read) => assert(read == value)
        case Miss(_)   => assert(false, "Expected Hit but got Miss")
    }

  private def testCache[K, V](f: (Cache[K, V], CacheStore) => Unit)(using
      cache: Cache[K, V]
  ): Unit =
    IO.withTemporaryDirectory { tmp =>
      val store = new FileBasedStore(tmp / "cache-store")
      f(cache, store)
    }

end CacheSpec
