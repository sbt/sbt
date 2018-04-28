package coursier.util

import utest._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

object TaskTests extends TestSuite {

  val tests = Tests {
    'tailRecM {
      import ExecutionContext.Implicits.global

      def countTo(i: Int): Task[Int] =
        Task.tailRecM(0) {
          case x if x >= i => Task.delay(Right(i))
          case toosmall => Task.delay(Left(toosmall + 1))
        }
      countTo(500000).map(_ == 500000).future().map(assert(_))
    }
  }
}
