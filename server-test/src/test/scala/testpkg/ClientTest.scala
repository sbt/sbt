/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ InputStream, OutputStream, PrintStream }
import sbt.internal.client.NetworkClient
import sbt.internal.util.Util
import scala.collection.mutable

object ClientTest extends AbstractServerTest {
  override val testDirectory: String = "client"
  object NullInputStream extends InputStream {
    override def read(): Int = {
      try this.synchronized(this.wait)
      catch { case _: InterruptedException => }
      -1
    }
  }
  val NullPrintStream = new PrintStream(_ => {}, false)
  class CachingPrintStream extends { val cos = new CachingOutputStream }
  with PrintStream(cos, true) {
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
  private def client(args: String*) =
    NetworkClient.client(
      testPath.toFile,
      args.toArray,
      NullInputStream,
      NullPrintStream,
      NullPrintStream,
      false
    )
  // This ensures that the completion command will send a tab that triggers
  // sbt to call definedTestNames or discoveredMainClasses if there hasn't
  // been a necessary compilation
  def tabs = new FixedInputStream('\t', '\t')
  private def complete(completionString: String): Seq[String] = {
    val cps = new CachingPrintStream
    NetworkClient.complete(
      testPath.toFile,
      Array(s"--completions=sbtn $completionString"),
      false,
      tabs,
      cps
    )
    cps.lines
  }
  test("exit success") { c =>
    assert(client("willSucceed") == 0)
  }
  test("exit failure") { _ =>
    assert(client("willFail") == 1)
  }
  test("two commands") { _ =>
    assert(client("compile;willSucceed") == 0)
  }
  test("two commands with failing second") { _ =>
    assert(client("compile;willFail") == 1)
  }
  test("two commands with leading failure") { _ =>
    assert(client("willFail;willSucceed") == 1)
  }
  test("three commands") { _ =>
    assert(client("compile;clean;willSucceed") == 0)
  }
  test("three commands with middle failure") { _ =>
    assert(client("compile;willFail;willSucceed") == 1)
  }
  test("compi completions") { _ =>
    val expected = Vector(
      "compile",
      "compile:",
      "compileAnalysisFile",
      "compileAnalysisFilename",
      "compileAnalysisTargetRoot",
      "compileEarly",
      "compileIncSetup",
      "compileIncremental",
      "compileJava",
      "compileOutputs",
      "compileProgress",
      "compileScalaBackend",
      "compileSplit",
      "compilers",
    )

    assert(complete("compi") == expected)
  }
  test("testOnly completions") { _ =>
    val testOnlyExpected = Vector(
      "testOnly",
      "testOnly/",
      "testOnly::",
      "testOnly;",
    )
    assert(complete("testOnly") == testOnlyExpected)

    val testOnlyOptionsExpected = Vector("--", ";", "test.pkg.FooSpec")
    assert(complete("testOnly ") == testOnlyOptionsExpected)
  }
  test("quote with semi") { _ =>
    assert(complete("\"compile; fooB") == Vector("compile; fooBar"))
  }
}
