/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ InputStream, OutputStream, PrintStream }
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit, TimeoutException }
import sbt.internal.client.NetworkClient
import sbt.internal.util.Util
import scala.collection.mutable

import org.scalatest.BeforeAndAfterEach
import scala.concurrent.duration.*
import sbt.internal.langserver.ErrorCodes
import sbt.internal.langserver.SbtExecParams
import sbt.internal.langserver.codec.JsonProtocol.given

class ClientTest extends AbstractServerTest with BeforeAndAfterEach:
  // without virtual IO the server writes the line to its own stdout, not to the channel
  override protected def serverJvmOptions: Vector[String] =
    Vector("-Djline.terminal=none", "-Dsbt.io.virtual=true", "-Dsbt.banner=false")
  override val testDirectory: String = "client"
  object NullInputStream extends InputStream:
    override def read(): Int =
      try this.synchronized(this.wait())
      catch
        case _: InterruptedException =>
      -1
  val NullPrintStream = new PrintStream(_ => {}, false)

  class CachingPrintStream(cos: CachingOutputStream = new CachingOutputStream)
      extends PrintStream(cos, true):
    def lines = cos.lines

  class CachingOutputStream extends OutputStream:
    private val byteBuffer = new mutable.ArrayBuffer[Byte]
    override def write(i: Int) = Util.ignoreResult(byteBuffer += i.toByte)
    def lines = new String(byteBuffer.toArray, "UTF-8").linesIterator.toSeq
  class FixedInputStream(keys: Char*) extends InputStream:
    var i = 0
    override def read(): Int =
      if i < keys.length then
        val res = keys(i).toInt
        i += 1
        res
      else -1

  override def afterEach(): Unit =
    // Wait between tests so the server can clean up the previous client connection.
    // TODO: probably sometimes NetworkClient doesn't close correclty.
    //   Maybe it should be refactored to use `ServerSession.shutdown`
    //   instead of its' own shutdown logic
    super.afterEach()
    Thread.sleep(500)

  private def background[R](f: => R): R =
    val result = new LinkedBlockingQueue[Either[Throwable, R]]
    val thread = new Thread("client-bg-thread"):
      setDaemon(true)
      start()
      override def run(): Unit =
        result.put(
          try Right(f)
          catch case e: Throwable => Left(e)
        )
    result.poll(1, TimeUnit.MINUTES) match
      case null =>
        thread.interrupt()
        thread.join(10000)
        throw new TimeoutException("background task did not complete within 1 minute")
      case Left(e)  => throw e
      case Right(r) => r
  private def client(args: String*): Int =
    background(
      NetworkClient.client(
        testPath.toFile,
        args.toArray,
        NullInputStream,
        NullPrintStream,
        NullPrintStream,
        false
      )
    )
  def clientWithStdoutLines(args: String*): (Int, Seq[String]) =
    val out = new CachingPrintStream
    val exitCode = background(
      NetworkClient.client(
        testPath.toFile,
        args.toArray,
        NullInputStream,
        out,
        NullPrintStream,
        false
      )
    )
    (exitCode, out.lines)
  // This ensures that the completion command will send a tab that triggers
  // sbt to call definedTestNames or discoveredMainClasses if there hasn't
  // been a necessary compilation
  def tabs = new FixedInputStream('\t', '\t')
  private def complete(completionString: String): Seq[String] =
    val cps = new CachingPrintStream
    background(
      NetworkClient.complete(
        testPath.toFile,
        Array(s"--completions=sbtn $completionString"),
        false,
        tabs,
        cps
      )
    )
    cps.lines
  test("exit success") {
    assert(client("willSucceed") == 0)
  }
  test("exit failure") {
    assert(client("willFail") == 1)
  }
  test("two commands") {
    assert(client("compile;willSucceed") == 0)
  }
  test("two commands with failing second") {
    assert(client("compile;willFail") == 1)
  }
  test("two commands with leading failure") {
    assert(client("willFail;willSucceed") == 1)
  }
  test("three commands") {
    assert(client("compile;willSucceed;willSucceed") == 0)
  }
  test("three commands with middle failure") {
    assert(client("compile;willFail;willSucceed") == 1)
  }
  test("batch client reports the action cache summary exactly once") {
    val (exitCode, lines) = clientWithStdoutLines("compile")
    assert(exitCode == 0)
    assert(lines.count(_.contains("elapsed time")) == 1, lines.mkString("\n"))
    assert(
      lines.exists(l => l.contains("elapsed time") && l.contains(", cache ")),
      lines.mkString("\n")
    )
  }
  test("batch client reports the result line exactly once when a task fails") {
    val (exitCode, lines) = clientWithStdoutLines("willFail")
    assert(exitCode == 1)
    assert(lines.count(_.contains("elapsed time")) == 1, lines.mkString("\n"))
    // the cache summary is what tells a server-written line from the client's own
    assert(
      lines.exists(l => l.contains("elapsed time") && l.contains(", cache ")),
      lines.mkString("\n")
    )
  }
  test("run") {
    val (exitCode, lines) = clientWithStdoutLines("run")
    assert(exitCode == 0)
    assert(
      lines.toList.exists(_.contains("running (fork) hello")),
      lines.toList.mkString(",")
    )
    assert(lines.count(_.contains("elapsed time")) == 1, lines.mkString("\n"))
  }
  test("a client-side job that returns Unit is still reported once") {
    val (exitCode, lines) = clientWithStdoutLines("runAsUnit")
    assert(exitCode == 0)
    assert(lines.count(_.contains("elapsed time")) == 1, lines.mkString("\n"))
  }
  test("a client-side job that then fails is still reported once") {
    val (exitCode, lines) = clientWithStdoutLines("runThenFail")
    assert(exitCode == 1)
    assert(lines.count(_.contains("elapsed time")) == 1, lines.mkString("\n"))
  }
  test("a failing sbt/exec is answered with the task's own error") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "sbt/exec", SbtExecParams("willFail")).get
    val error = svr.session.waitForResponseMsg(60.seconds, id).get.error
    assert(error.exists(_.code == ErrorCodes.InternalError), error.toString)
    assert(error.exists(_.message.contains("willFail")), error.toString)
  }
  test("compi completions") {
    val expected = Vector(
      "compile",
      "compileAnalysisFile",
      "compileAnalysisFilename",
      "compileAnalysisTargetRoot",
      "compileEarly",
      "compileIncSetup",
      "compileIncremental",
      "compileJava",
      "compileOrder",
      "compileOutputs",
      "compileProgress",
      "compileScalaBackend",
      "compileSplit",
      "compilerCache",
      "compilers",
    )

    assert(complete("compi").toVector == expected)
  }
  test("testOnly completions") {
    val testOnlyExpected = Vector(
      "testOnly",
      "testOnly/",
      "testOnly;",
    )
    assert(complete("testOnly") == testOnlyExpected)

    val testOnlyOptionsExpected =
      Vector("--", "--cache_test_result=", "--test_summary=", ";", "test.pkg.FooSpec")
    assert(complete("testOnly ") == testOnlyOptionsExpected)
  }
  test("quote with semi") {
    assert(complete("\"compile; fooB") == Vector("compile; fooBar"))
  }
  test("forked run with connectInput relays stdout to --client") {
    val (exit, lines) = clientWithStdoutLines("serverFork/run")
    assert(exit == 0, s"non-zero exit; lines=${lines.mkString("\n")}")
    assert(
      lines.exists(_.contains("STDOUT_MARKER_9185")),
      s"missing STDOUT_MARKER_9185 in: ${lines.mkString("\n")}"
    )
  }
end ClientTest
