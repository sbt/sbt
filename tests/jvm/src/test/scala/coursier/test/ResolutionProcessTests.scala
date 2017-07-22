package coursier.test

import java.util.concurrent.ConcurrentHashMap

import coursier.{Fetch, Module}
import coursier.core.ResolutionProcess
import utest._

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scalaz.{-\/, \/-}
import scalaz.concurrent.Task

object ResolutionProcessTests extends TestSuite {

  val tests = TestSuite {

    'fetchAll - {

      // check that tasks fetching different versions of the same module are spawned sequentially
      // rather than all at once
      def check(extra: Int): Unit = {

        val mod = Module("org", "name")
        val modVers = (1 to (9 + extra))
          .map(_.toString)
          .map((mod, _))

        val called = new ConcurrentHashMap[String, Unit]

        val fetch: Fetch.Metadata[Task] = {

          case Seq((`mod`, "9")) =>
            // never calls the callback
            Task.async { _ =>
              called.put("9", ())
              ()
            }

          case Seq(mv @ (`mod`, v)) =>
            Task.async { cb =>
              called.put(v, ())
              cb(\/-(Seq((mv, -\/(Seq("w/e"))))))
            }

          case _ => sys.error(s"Cannot happen ($modVers)")
        }

        val res = ResolutionProcess.fetchAll(modVers, fetch)
          .timed(1.second)
          .attempt
          .unsafePerformSync

        // must have timed out
        assert(res.swap.exists[Throwable] { case _: java.util.concurrent.TimeoutException => true; case _ => false })

        val called0 = called.asScala.iterator.map(_._1).toSet
        val expectedCalled = (0 to extra)
          .map(9 + _)
          .map(_.toString)
          .toSet
        assert(called0 == expectedCalled)
      }

      * - check(0)
      * - check(1)
      * - check(3)
    }

  }

}
