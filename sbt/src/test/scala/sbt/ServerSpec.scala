/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import org.scalatest._
import scala.concurrent._
import java.io.{ InputStream, OutputStream }
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ ThreadFactory, ThreadPoolExecutor }
import sbt.protocol.ClientSocket

class ServerSpec extends AsyncFlatSpec with Matchers {
  import ServerSpec._

  "server" should "start" in {
    withBuildSocket("handshake") { (out, in, tkn) =>
      writeLine(
        """{ "jsonrpc": "2.0", "id": 3, "method": "sbt/setting", "params": { "setting": "root/name" } }""",
        out)
      Thread.sleep(100)
      val l2 = contentLength(in)
      println(l2)
      readLine(in)
      readLine(in)
      val x2 = readContentLength(in, l2)
      println(x2)
      assert(1 == 1)
    }
  }
}

object ServerSpec {
  private val serverTestBase: File = new File(".").getAbsoluteFile / "sbt" / "src" / "server-test"
  private val nextThreadId = new AtomicInteger(1)
  private val threadGroup = Thread.currentThread.getThreadGroup()
  val readBuffer = new Array[Byte](4096)
  var buffer: Vector[Byte] = Vector.empty
  var bytesRead = 0
  private val delimiter: Byte = '\n'.toByte
  private val RetByte = '\r'.toByte

  private val threadFactory = new ThreadFactory() {
    override def newThread(runnable: Runnable): Thread = {
      val thread =
        new Thread(threadGroup,
                   runnable,
                   s"sbt-test-server-threads-${nextThreadId.getAndIncrement}")
      // Do NOT setDaemon because then the code in TaskExit.scala in sbt will insta-kill
      // the backgrounded process, at least for the case of the run task.
      thread
    }
  }

  private val executor = new ThreadPoolExecutor(
    0, /* corePoolSize */
    1, /* maxPoolSize, max # of servers */
    2,
    java.util.concurrent.TimeUnit.SECONDS,
    /* keep alive unused threads this long (if corePoolSize < maxPoolSize) */
    new java.util.concurrent.SynchronousQueue[Runnable](),
    threadFactory
  )

  def backgroundRun(baseDir: File, args: Seq[String]): Unit = {
    executor.execute(new Runnable {
      def run(): Unit = {
        RunFromSourceMain.run(baseDir, args)
      }
    })
  }

  def shutdown(): Unit = executor.shutdown()

  def withBuildSocket(testBuild: String)(
      f: (OutputStream, InputStream, Option[String]) => Future[Assertion]): Future[Assertion] = {
    IO.withTemporaryDirectory { temp =>
      IO.copyDirectory(serverTestBase / testBuild, temp / testBuild)
      withBuildSocket(temp / testBuild)(f)
    }
  }

  def sendJsonRpc(message: String, out: OutputStream): Unit = {
    writeLine(s"""Content-Length: ${message.size + 2}""", out)
    writeLine("", out)
    writeLine(message, out)
  }

  def contentLength(in: InputStream): Int = {
    readLine(in) map { line =>
      line.drop(16).toInt
    } getOrElse (0)
  }

  def readLine(in: InputStream): Option[String] = {
    if (buffer.isEmpty) {
      val bytesRead = in.read(readBuffer)
      if (bytesRead > 0) {
        buffer = buffer ++ readBuffer.toVector.take(bytesRead)
      }
    }
    val delimPos = buffer.indexOf(delimiter)
    if (delimPos > 0) {
      val chunk0 = buffer.take(delimPos)
      buffer = buffer.drop(delimPos + 1)
      // remove \r at the end of line.
      if (chunk0.size > 0 && chunk0.indexOf(RetByte) == chunk0.size - 1)
        Some(new String(chunk0.dropRight(1).toArray, "utf-8"))
      else Some(new String(chunk0.toArray, "utf-8"))
    } else None // no EOL yet, so skip this turn.
  }

  def readContentLength(in: InputStream, length: Int): Option[String] = {
    if (buffer.isEmpty) {
      val bytesRead = in.read(readBuffer)
      if (bytesRead > 0) {
        buffer = buffer ++ readBuffer.toVector.take(bytesRead)
      }
    }
    if (length <= buffer.size) {
      val chunk = buffer.take(length)
      buffer = buffer.drop(length)
      Some(new String(chunk.toArray, "utf-8"))
    } else None // have not read enough yet, so skip this turn.
  }

  def writeLine(s: String, out: OutputStream): Unit = {
    def writeEndLine(): Unit = {
      val retByte: Byte = '\r'.toByte
      val delimiter: Byte = '\n'.toByte
      out.write(retByte.toInt)
      out.write(delimiter.toInt)
      out.flush
    }

    if (s != "") {
      out.write(s.getBytes("UTF-8"))
    }
    writeEndLine
  }

  def withBuildSocket(baseDirectory: File)(
      f: (OutputStream, InputStream, Option[String]) => Future[Assertion]): Future[Assertion] = {
    backgroundRun(baseDirectory, Nil)

    val portfile = baseDirectory / "project" / "target" / "active.json"

    def waitForPortfile(n: Int): Unit =
      if (portfile.exists) ()
      else {
        if (n <= 0) sys.error(s"Timeout. $portfile is not found.")
        else {
          Thread.sleep(1000)
          waitForPortfile(n - 1)
        }
      }
    waitForPortfile(10)
    val (sk, tkn) = ClientSocket.socket(portfile)
    val out = sk.getOutputStream
    val in = sk.getInputStream

    sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": 1, "method": "initialize", "params": { "initializationOptions": { } } }""",
      out)

    try {
      f(out, in, tkn)
    } finally {
      sendJsonRpc(
        """{ "jsonrpc": "2.0", "id": 9, "method": "sbt/exec", "params": { "commandLine": "exit" } }""",
        out)
      shutdown()
    }
  }
}
