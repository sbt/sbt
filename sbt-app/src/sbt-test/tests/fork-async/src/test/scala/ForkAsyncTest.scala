import utest._

import utest._
import utest.framework._

import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.Success

object ForkAsyncTest extends TestSuite {
  val g = ExecutionContext.global
  val n = 10
  val (testNames, promises) = (1 to n).map(i => Tree(s"$i") -> Promise[Unit]).unzip
  val testTrees = promises.zipWithIndex.map { case (p, i) =>
    new TestCallTree(Left {
      if (i == (n - 1)) promises.foreach(p => g.execute(() => p.tryComplete(Success(()))))
      p.future
    })
  }
  val tests =
    Tests(nameTree = Tree("async", testNames: _*), callTree = new TestCallTree(Right(testTrees)))
}
