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

class ClientTest extends AbstractServerTest with BeforeAndAfterEach {
  override val testDirectory: String = "client"
  object NullInputStream extends InputStream {
    override def read(): Int = {
      try this.synchronized(this.wait())
      catch { case _: InterruptedException => }
      -1
    }
  }
  val NullPrintStream = new PrintStream(_ => {}, false)

  class CachingPrintStream(cos: CachingOutputStream = new CachingOutputStream)
      extends PrintStream(cos, true) {
    def lines = cos.lines
  }

  class CachingOutputStream extends OutputStream {
    private val byteBuffer = new mutable.ArrayBuffer[Byte]
    override def write(i: Int) = Util.ignoreResult(byteBuffer += i.toByte)
    def lines = new String(byteBuffer.toArray, "UTF-8").linesIterator.toSeq
  }
  class FixedInputStream(keys: Char*) extends InputStream {
    var i = 0
    override def read(): Int = {
      if (i < keys.length) {
        val res = keys(i).toInt
        i += 1
        res
      } else -1
    }
  }

  override def afterEach(): Unit = {
    // Wait between tests so the server can clean up the previous client connection.
    // TODO: probably sometimes NetworkClient doesn't close correclty.
    //   Maybe it should be refactored to use `ServerSession.shutdown`
    //   instead of its' own shutdown logic
    super.afterEach()
    Thread.sleep(500)
  }

  private def background[R](f: => R): R = {
    val result = new LinkedBlockingQueue[Either[Throwable, R]]
    val thread = new Thread("client-bg-thread") {
      setDaemon(true)
      start()
      override def run(): Unit =
        result.put(
          try Right(f)
          catch { case e: Throwable => Left(e) }
        )
    }
    result.poll(1, TimeUnit.MINUTES) match {
      case null =>
        thread.interrupt()
        thread.join(10000)
        throw new TimeoutException("background task did not complete within 1 minute")
      case Left(e)  => throw e
      case Right(r) => r
    }
  }
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
  def clientWithStdoutLines(args: String*): (Int, Seq[String]) = {
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
  }
  // This ensures that the completion command will send a tab that triggers
  // sbt to call definedTestNames or discoveredMainClasses if there hasn't
  // been a necessary compilation
  def tabs = new FixedInputStream('\t', '\t')
  private def complete(completionString: String): Seq[String] = {
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
  }
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
  test("run") {
    val (exitCode, lines) = clientWithStdoutLines("run")
    assert(exitCode == 0)
    assert(
      lines.toList.exists(_.contains("running (fork) hello")),
      lines.toList.mkString(",")
    )
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

    val testOnlyOptionsExpected = Vector("--", ";", "test.pkg.FooSpec")
    assert(complete("testOnly ") == testOnlyOptionsExpected)
  }
  test("quote with semi") {
    assert(complete("\"compile; fooB") == Vector("compile; fooBar"))
  }
}
