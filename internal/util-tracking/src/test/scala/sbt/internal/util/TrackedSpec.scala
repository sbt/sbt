package sbt.internal.util

import sbt.io.IO
import sbt.io.syntax._

import CacheImplicits._

import sjsonnew.IsoString
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }

import scala.json.ast.unsafe.JValue

class TrackedSpec extends UnitSpec {

  implicit val isoString: IsoString[JValue] = IsoString.iso(CompactPrinter.apply, Parser.parseUnsafe)

  "lastOutput" should "store the last output" in {
    withStore { store =>

      val value = 5
      val otherValue = 10

      val res0 =
        Tracked.lastOutput[Int, Int](store) {
          case (in, None) =>
            assert(in === value)
            in
          case (in, Some(_)) =>
            fail()
        }(implicitly)(value)
      assert(res0 === value)

      val res1 =
        Tracked.lastOutput[Int, Int](store) {
          case (in, None) =>
            fail()
          case (in, Some(read)) =>
            assert(in === otherValue)
            assert(read === value)
            read
        }(implicitly)(otherValue)
      assert(res1 === value)

      val res2 =
        Tracked.lastOutput[Int, Int](store) {
          case (in, None) =>
            fail()
          case (in, Some(read)) =>
            assert(in === otherValue)
            assert(read === value)
            read
        }(implicitly)(otherValue)
      assert(res2 === value)
    }
  }

  "inputChanged" should "detect that the input has not changed" in {
    withStore { store =>
      val input0 = 0

      val res0 =
        Tracked.inputChanged[Int, Int](store) {
          case (true, in) =>
            assert(in === input0)
            in
          case (false, in) =>
            fail()
        }(implicitly, implicitly)(input0)
      assert(res0 === input0)

      val res1 =
        Tracked.inputChanged[Int, Int](store) {
          case (true, in) =>
            fail()
          case (false, in) =>
            assert(in === input0)
            in
        }(implicitly, implicitly)(input0)
      assert(res1 === input0)

    }
  }

  it should "detect that the input has changed" in {
    withStore { store =>
      val input0 = 0
      val input1 = 1

      val res0 =
        Tracked.inputChanged[Int, Int](store) {
          case (true, in) =>
            assert(in === input0)
            in
          case (false, in) =>
            fail()
        }(implicitly, implicitly)(input0)
      assert(res0 === input0)

      val res1 =
        Tracked.inputChanged[Int, Int](store) {
          case (true, in) =>
            assert(in === input1)
            in
          case (false, in) =>
            fail()
        }(implicitly, implicitly)(input1)
      assert(res1 === input1)

    }
  }

  "tstamp tracker" should "have a timestamp of 0 on first invocation" in {
    withStore { store =>
      Tracked.tstamp(store) { last =>
        assert(last === 0)
      }
    }
  }

  it should "provide the last time a function has been evaluated" in {
    withStore { store =>

      Tracked.tstamp(store) { last =>
        assert(last === 0)
      }

      Tracked.tstamp(store) { last =>
        val difference = System.currentTimeMillis - last
        assert(difference < 1000)
      }
    }
  }

  private def withStore(f: CacheStore => Unit): Unit =
    IO.withTemporaryDirectory { tmp =>
      val store = new FileBasedStore(tmp / "cache-store", Converter)
      f(store)
    }

}