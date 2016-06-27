package sbt.internal.util

import sbt.io.IO
import sbt.io.syntax._

import CacheImplicits._

import sjsonnew.{ Builder, deserializationError, IsoString, JsonFormat, Unbuilder }
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, FixedParser }

import scala.json.ast.unsafe.JValue

class SingletonCacheSpec extends UnitSpec {

  case class ComplexType(val x: Int, y: String, z: List[Int])
  object ComplexType {
    implicit val format: JsonFormat[ComplexType] =
      new JsonFormat[ComplexType] {
        override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): ComplexType = {
          jsOpt match {
            case Some(js) =>
              unbuilder.beginObject(js)
              val x = unbuilder.readField[Int]("x")
              val y = unbuilder.readField[String]("y")
              val z = unbuilder.readField[List[Int]]("z")
              unbuilder.endObject()
              ComplexType(x, y, z)

            case None =>
              deserializationError("Exception JObject but found None")
          }
        }

        override def write[J](obj: ComplexType, builder: Builder[J]): Unit = {
          builder.beginObject()
          builder.addField("x", obj.x)
          builder.addField("y", obj.y)
          builder.addField("z", obj.z)
          builder.endObject()
        }
      }
  }

  implicit val isoString: IsoString[JValue] = IsoString.iso(CompactPrinter.apply, FixedParser.parseUnsafe)

  "A singleton cache" should "throw an exception if read without being written previously" in {
    testCache[Int] {
      case (cache, store) =>
        intercept[Exception] {
          cache.read(store)
        }
        ()
    }
  }

  it should "write a very simple value" in {
    testCache[Int] {
      case (cache, store) =>
        cache.write(store, 5)
    }
  }

  it should "return the simple value that has been previously written" in {
    testCache[Int] {
      case (cache, store) =>
        val value = 5
        cache.write(store, value)
        val read = cache.read(store)

        assert(read === value)
    }
  }

  it should "write a complex value" in {
    testCache[ComplexType] {
      case (cache, store) =>
        val value = ComplexType(1, "hello, world!", (1 to 10 by 3).toList)
        cache.write(store, value)
        val read = cache.read(store)

        assert(read === value)
    }
  }

  private def testCache[T](f: (SingletonCache[T], CacheStore) => Unit)(implicit cache: SingletonCache[T]): Unit =
    IO.withTemporaryDirectory { tmp =>
      val store = new FileBasedStore(tmp / "cache-store", Converter)
      f(cache, store)
    }

}