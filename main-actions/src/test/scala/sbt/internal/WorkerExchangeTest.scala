package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import hedgehog.core.{ ShrinkLimit, SuccessCount }
import hedgehog.core.Result
import scala.sys.process.Process

object WorkerExchangeTest extends Properties:
  given Gen[WorkerConnection] =
    Gen.choice1(Gen.constant(WorkerConnection.Stdio), Gen.constant(WorkerConnection.Tcp))

  def gen[A1: Gen]: Gen[A1] = summon[Gen[A1]]

  override lazy val tests: List[Test] = List(
    propertyN("non-jsonrpc should return exit code 1", propBadInput, 10),
    propertyN("bye should return response json with a result", propBye, 10),
  )

  def propertyN(name: String, result: => Property, n: Int): Test =
    Test(name, result)
      .config(_.copy(testLimit = SuccessCount(n), shrinkLimit = ShrinkLimit(n * 10)))

  def propBadInput: Property =
    for
      ct <- gen[WorkerConnection].forAll
      w = WorkerExchange.startWorker(ForkOptions(), Nil, ct)
    yield
      w.println("{}")
      val exitCode = w.blockForExitCode()
      Result.assert(exitCode == 1)

  val intGen = Gen.int(Range.linear(1, 100))

  def propBye: Property =
    for
      ct <- gen[WorkerConnection].forAll
      i <- intGen.forAll
      w = WorkerExchange.startWorker(ForkOptions(), Nil, ct)
    yield withListener: l =>
      w.println(s"""{"jsonrpc": "2.0", "method": "bye", "params": {}, "id": $i}""")
      val exitCode = w.blockForExitCode()
      l.awaitResponse()
      Result
        .assert(exitCode == 0)
        .and(Result.assert(l.sb.toString() == s"""{ "jsonrpc": "2.0", "result": 0, "id": $i }"""))
        .log(s"\"${l.sb.toString()}\"")

  def withListener[A1](f: ConcreteListener => A1) =
    val l = ConcreteListener()
    try
      WorkerExchange.registerListener(l)
      f(l)
    finally WorkerExchange.unregisterListener(l)

  class ConcreteListener extends WorkerResponseListener:
    import java.util.concurrent.{ CountDownLatch, TimeUnit }
    val sb = StringBuilder()
    private val latch = CountDownLatch(1)
    def notifyExit(p: Process): Unit = ()
    def apply(line: String): Unit =
      sb.append(line)
      latch.countDown()
    def awaitResponse(): Unit = latch.await(30, TimeUnit.SECONDS)
end WorkerExchangeTest
