package sbt.util

import sbt.io.IO
import sbt.io.syntax._

import CacheImplicits._

import sjsonnew.IsoString
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }

import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import org.scalatest.FlatSpec

class CacheSpec extends FlatSpec {

  implicit val isoString: IsoString[JValue] =
    IsoString.iso(CompactPrinter.apply, Parser.parseUnsafe)

  "A cache" should "NOT throw an exception if read without being written previously" in {
    testCache[String, Int] {
      case (cache, store) =>
        cache(store)("missing") match {
          case Hit(_)  => fail
          case Miss(_) => ()
        }
    }
  }

  it should "write a very simple value" in {
    testCache[String, Int] {
      case (cache, store) =>
        cache(store)("missing") match {
          case Hit(_)       => fail
          case Miss(update) => update(5)
        }
    }
  }

  it should "be updatable" in {
    testCache[String, Int] {
      case (cache, store) =>
        val value = 5
        cache(store)("someKey") match {
          case Hit(_)       => fail
          case Miss(update) => update(value)
        }

        cache(store)("someKey") match {
          case Hit(read) => assert(read === value)
          case Miss(_)   => fail
        }
    }
  }

  it should "return the value that has been previously written" in {
    testCache[String, Int] {
      case (cache, store) =>
        val key = "someKey"
        val value = 5
        cache(store)(key) match {
          case Hit(_)       => fail
          case Miss(update) => update(value)
        }

        cache(store)(key) match {
          case Hit(read) => assert(read === value)
          case Miss(_)   => fail
        }
    }
  }

  private def testCache[K, V](f: (Cache[K, V], CacheStore) => Unit)(
      implicit cache: Cache[K, V]
  ): Unit =
    IO.withTemporaryDirectory { tmp =>
      val store = new FileBasedStore(tmp / "cache-store", Converter)
      f(cache, store)
    }

}
