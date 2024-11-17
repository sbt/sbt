/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sbt.io.IO
import sbt.io.syntax._

import CacheImplicits.given

import org.scalatest.flatspec.AnyFlatSpec

class CacheSpec extends AnyFlatSpec {

  "A cache" should "NOT throw an exception if read without being written previously" in {
    testCache[String, Int] { case (cache, store) =>
      cache(store)("missing") match {
        case Hit(_)  => fail()
        case Miss(_) => ()
      }
    }
  }

  it should "write a very simple value" in {
    testCache[String, Int] { case (cache, store) =>
      cache(store)("missing") match {
        case Hit(_)       => fail()
        case Miss(update) => update(5)
      }
    }
  }

  it should "be updatable" in {
    testCache[String, Int] { case (cache, store) =>
      val value = 5
      cache(store)("someKey") match {
        case Hit(_)       => fail()
        case Miss(update) => update(value)
      }

      cache(store)("someKey") match {
        case Hit(read) => assert(read === value); ()
        case Miss(_)   => fail()
      }
    }
  }

  it should "return the value that has been previously written" in {
    testCache[String, Int] { case (cache, store) =>
      val key = "someKey"
      val value = 5
      cache(store)(key) match {
        case Hit(_)       => fail()
        case Miss(update) => update(value)
      }

      cache(store)(key) match {
        case Hit(read) => assert(read === value); ()
        case Miss(_)   => fail()
      }
    }
  }

  private def testCache[K, V](f: (Cache[K, V], CacheStore) => Unit)(using
      cache: Cache[K, V]
  ): Unit =
    IO.withTemporaryDirectory { tmp =>
      val store = new FileBasedStore(tmp / "cache-store")
      f(cache, store)
    }

}
