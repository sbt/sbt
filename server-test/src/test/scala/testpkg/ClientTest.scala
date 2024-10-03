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

class ClientTest extends AbstractServerTest {
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
  private def background[R](f: => R): R = {
    val result = new LinkedBlockingQueue[R]
    val thread = new Thread("client-bg-thread") {
      setDaemon(true)
      start()
      override def run(): Unit = result.put(f)
    }
    result.poll(1, TimeUnit.MINUTES) match {
      case null =>
        thread.interrupt()
        thread.join(5000)
        throw new TimeoutException
      case r => r
    }
  }
  private def client(args: String*): Int = {
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
    assert(client("compile;clean;willSucceed") == 0)
  }
  test("three commands with middle failure") {
    assert(client("compile;willFail;willSucceed") == 1)
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
