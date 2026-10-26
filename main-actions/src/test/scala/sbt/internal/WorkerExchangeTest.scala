package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import hedgehog.core.{ ShrinkLimit, SuccessCount }
import hedgehog.core.Result
import java.io.{ ByteArrayInputStream, InputStream, PipedInputStream, PipedOutputStream }
import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*
import scala.sys.process.Process
import scala.util.Using
import _root_.sbt.util.Logger

object WorkerExchangeTest extends Properties:
  given Gen[WorkerConnection] =
    Gen.choice1(
      Gen.constant(WorkerConnection.Stdio),
      Gen.constant(WorkerConnection.Tcp),
      Gen.constant(WorkerConnection.Ipc(WorkerExchange.newIpcSocketPath())),
    )

  def gen[A1: Gen]: Gen[A1] = summon[Gen[A1]]

  override lazy val tests: List[Test] = List(
    propertyN("non-jsonrpc should return exit code 1", propBadInput, 10),
    propertyN("bye should return response json with a result", propBye, 10),
    example("startWorker(fo, extraCp) auto-detects a working connection type", exampleAutoDetect),
    property("readLines preserves lines", propReadLines),
    example("readLines reads a 32MB line in linear time", exampleLongLine),
    example("readLines stops at a closed connection", exampleReadLinesClosed),
    example("notifyExit ignores another worker's exit", exampleNotifyExitOtherWorker),
  )

  def exampleReadLinesClosed: Result =
    val bytes = ByteArrayInputStream("a\nb\n".getBytes(StandardCharsets.UTF_8))
    val in = new InputStream:
      def read(): Int =
        val b = bytes.read()
        if b < 0 then throw ClosedChannelException() else b
    val actual = ListBuffer.empty[String]
    WorkerExchange.readLines(in)(actual += _)
    Result.assert(actual.toList == List("a", "b"))

  def exitedProcess: Process = new Process:
    def isAlive(): Boolean = false
    def exitValue(): Int = 0
    def destroy(): Unit = ()

  def exampleNotifyExitOtherWorker: Result =
    val own = exitedProcess
    val react = React(1L, Logger.Null, Nil, mutable.Map.empty, own)
    react.notifyExit(exitedProcess)
    val ignored = !react.promise.isCompleted
    react.notifyExit(own)
    Result.assert(ignored).and(Result.assert(react.promise.isCompleted))

  def propReadLines: Property =
    for lines <- Gen.string(Gen.unicode, Range.linear(0, 50)).list(Range.linear(0, 10)).forAll
    yield
      val clean = lines.map(_.filterNot(c => c == '\n' || c == '\r'))
      val in = ByteArrayInputStream(clean.map(_ + "\n").mkString.getBytes(StandardCharsets.UTF_8))
      val actual = ListBuffer.empty[String]
      WorkerExchange.readLines(in)(actual += _)
      Result.assert(actual.toList == clean)

  def exampleLongLine: Result =
    val size = 32 * 1024 * 1024
    val out = PipedOutputStream()
    val in = PipedInputStream(out, 64 * 1024)
    val writer = Thread: () =>
      Using.resource(out): o =>
        val chunk = ("x" * 8192).getBytes(StandardCharsets.UTF_8)
        (0 until size / chunk.length).foreach(_ => o.write(chunk))
        o.write('\n')
    val start = System.nanoTime()
    writer.start()
    val lengths = ListBuffer.empty[Int]
    WorkerExchange.readLines(in)(lengths += _.length)
    val elapsed = (System.nanoTime() - start).nanos
    Result
      .assert(lengths.toList == List(size))
      .and(Result.assert(elapsed < 20.seconds))
      .log(s"elapsed: ${elapsed.toMillis}ms")

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

  def exampleAutoDetect: Result =
    val w = WorkerExchange.startWorker(ForkOptions(), Nil)
    withListener: l =>
      w.println("""{"jsonrpc": "2.0", "method": "bye", "params": {}, "id": 1}""")
      val exitCode = w.blockForExitCode()
      l.awaitResponse()
      Result
        .assert(exitCode == 0)
        .and(Result.assert(l.sb.toString() == """{ "jsonrpc": "2.0", "result": 0, "id": 1 }"""))
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
