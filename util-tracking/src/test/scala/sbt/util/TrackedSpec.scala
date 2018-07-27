package sbt.util

import org.scalatest.FlatSpec
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.CacheImplicits._

import scala.concurrent.Promise

class TrackedSpec extends FlatSpec {
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
      val input0 = "foo"

      val res0 =
        Tracked.inputChanged[String, String](store) {
          case (true, in) =>
            assert(in === input0)
            in
          case (false, in) =>
            fail()
        }(implicitly, implicitly)(input0)
      assert(res0 === input0)

      val res1 =
        Tracked.inputChanged[String, String](store) {
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

  "outputChanged" should "detect that the output has not changed" in {
    withStore { store =>
      val beforeCompletion: String = "before-completion"
      val afterCompletion: String = "after-completion"
      val sideEffectCompleted = Promise[Unit]
      val p0: () => String = () => {
        if (sideEffectCompleted.isCompleted) {
          afterCompletion
        } else {
          sideEffectCompleted.success(())
          beforeCompletion
        }
      }
      val firstExpectedResult = "first-result"
      val secondExpectedResult = "second-result"

      val res0 =
        Tracked.outputChanged[String, String](store) {
          case (true, in) =>
            assert(in === beforeCompletion)
            firstExpectedResult
          case (false, _) =>
            fail()
        }(implicitly)(p0)
      assert(res0 === firstExpectedResult)

      val res1 =
        Tracked.outputChanged[String, String](store) {
          case (true, _) =>
            fail()
          case (false, in) =>
            assert(in === afterCompletion)
            secondExpectedResult
        }(implicitly)(p0)
      assert(res1 === secondExpectedResult)

      ()
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
      val store = CacheStore(tmp / "cache-store")
      f(store)
    }

}
