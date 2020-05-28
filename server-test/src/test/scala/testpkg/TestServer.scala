/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ File, IOException }
import java.util.concurrent.TimeoutException

import verify._
import sbt.RunFromSourceMain
import sbt.io.IO
import sbt.io.syntax._
import sbt.protocol.ClientSocket

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{ Success, Try }

trait AbstractServerTest extends TestSuite[Unit] {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  private var temp: File = _
  var svr: TestServer = _
  def testDirectory: String

  private val targetDir: File = {
    val p0 = new File("..").getAbsoluteFile.getCanonicalFile / "target"
    val p1 = new File("target").getAbsoluteFile
    if (p0.exists) p0
    else p1
  }

  override def setupSuite(): Unit = {
    temp = targetDir / "test-server" / testDirectory
    if (temp.exists) {
      IO.delete(temp)
    }
    val classpath = sys.props.get("sbt.server.classpath") match {
      case Some(s: String) => s.split(java.io.File.pathSeparator).map(file)
      case _               => throw new IllegalStateException("No server classpath was specified.")
    }
    val sbtVersion = sys.props.get("sbt.server.version") match {
      case Some(v: String) => v
      case _               => throw new IllegalStateException("No server version was specified.")
    }
    val scalaVersion = sys.props.get("sbt.server.scala.version") match {
      case Some(v: String) => v
      case _               => throw new IllegalStateException("No server scala version was specified.")
    }
    svr = TestServer.get(testDirectory, scalaVersion, sbtVersion, classpath, temp)
  }
  override def tearDownSuite(): Unit = {
    svr.bye()
    svr = null
    IO.delete(temp)
  }
  override def setup(): Unit = ()
  override def tearDown(env: Unit): Unit = ()
}

object TestServer {
  // forking affects this
  private val serverTestBase: File = {
    val p0 = new File(".").getAbsoluteFile / "server-test" / "src" / "server-test"
    val p1 = new File(".").getAbsoluteFile / "src" / "server-test"
    if (p0.exists) p0
    else p1
  }

  def get(
      testBuild: String,
      scalaVersion: String,
      sbtVersion: String,
      classpath: Seq[File],
      temp: File
  ): TestServer = {
    println(s"Starting test server $testBuild")
    IO.copyDirectory(serverTestBase / testBuild, temp / testBuild)

    // Each test server instance will be executed in a Thread pool separated from the tests
    val testServer = TestServer(temp / testBuild, scalaVersion, sbtVersion, classpath)
    // checking last log message after initialization
    // if something goes wrong here the communication streams are corrupted, restarting
    val init =
      Try {
        testServer.waitForString(30.seconds) { s =>
          println(s)
          s contains """"message":"Done""""
        }
      }
    init.get
    testServer
  }

  def withTestServer(
      testBuild: String
  )(f: TestServer => Future[Unit]): Future[Unit] = {
    println(s"Starting test")
    IO.withTemporaryDirectory { temp =>
      IO.copyDirectory(serverTestBase / testBuild, temp / testBuild)
      withTestServer(testBuild, temp / testBuild)(f)
    }
  }

  def withTestServer(testBuild: String, baseDirectory: File)(
      f: TestServer => Future[Unit]
  ): Future[Unit] = {
    val classpath = sys.props.get("sbt.server.classpath") match {
      case Some(s: String) => s.split(java.io.File.pathSeparator).map(file)
      case _               => throw new IllegalStateException("No server classpath was specified.")
    }
    val sbtVersion = sys.props.get("sbt.server.version") match {
      case Some(v: String) => v
      case _               => throw new IllegalStateException("No server version was specified.")
    }
    val scalaVersion = sys.props.get("sbt.server.scala.version") match {
      case Some(v: String) => v
      case _               => throw new IllegalStateException("No server scala version was specified.")
    }
    // Each test server instance will be executed in a Thread pool separated from the tests
    val testServer = TestServer(baseDirectory, scalaVersion, sbtVersion, classpath)
    // checking last log message after initialization
    // if something goes wrong here the communication streams are corrupted, restarting
    val init =
      Try {
        testServer.waitForString(30.seconds) { s =>
          if (s.nonEmpty) println(s)
          s contains """"message":"Done""""
        }
      }

    init match {
      case Success(_) =>
        try {
          f(testServer)
        } finally {
          try {
            testServer.bye()
          } finally {}
        }
      case _ =>
        try {
          testServer.bye()
        } finally {}
        hostLog("Server started but not connected properly... restarting...")
        withTestServer(testBuild)(f)
    }
  }

  def hostLog(s: String): Unit = {
    println(s"""[${scala.Console.MAGENTA}build-1${scala.Console.RESET}] $s""")
  }
}

case class TestServer(
    baseDirectory: File,
    scalaVersion: String,
    sbtVersion: String,
    classpath: Seq[File]
) {
  import scala.concurrent.ExecutionContext.Implicits._
  import TestServer.hostLog

  val readBuffer = new Array[Byte](40960)
  var buffer: Vector[Byte] = Vector.empty
  var bytesRead = 0
  private val delimiter: Byte = '\n'.toByte
  private val RetByte = '\r'.toByte

  hostLog("fork to a new sbt instance")
  val process = RunFromSourceMain.fork(baseDirectory, scalaVersion, sbtVersion, classpath)

  lazy val portfile = baseDirectory / "project" / "target" / "active.json"

  def portfileIsEmpty(): Boolean =
    try IO.read(portfile).isEmpty
    catch { case _: IOException => true }
  def waitForPortfile(duration: FiniteDuration): Unit = {
    val deadline = duration.fromNow
    var nextLog = 10.seconds.fromNow
    while (portfileIsEmpty && !deadline.isOverdue && process.isAlive) {
      if (nextLog.isOverdue) {
        hostLog("waiting for the server...")
        nextLog = 10.seconds.fromNow
      }
    }
    if (deadline.isOverdue) sys.error(s"Timeout. $portfile is not found.")
    if (!process.isAlive) sys.error(s"Server unexpectedly terminated.")
  }
  private val waitDuration: FiniteDuration = 120.seconds
  hostLog(s"wait $waitDuration until the server is ready to respond")
  waitForPortfile(90.seconds)

  // make connection to the socket described in the portfile
  var (sk, _) = ClientSocket.socket(portfile)
  var out = sk.getOutputStream
  var in = sk.getInputStream

  // initiate handshake
  sendJsonRpc(
    """{ "jsonrpc": "2.0", "id": 1, "method": "initialize", "params": { "initializationOptions": { } } }"""
  )

  def resetConnection() = {
    sk = ClientSocket.socket(portfile)._1
    out = sk.getOutputStream
    in = sk.getInputStream

    sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": 1, "method": "initialize", "params": { "initializationOptions": { } } }"""
    )
  }

  def test(f: TestServer => Future[Assertion]): Future[Assertion] = {
    f(this)
  }

  def bye(): Unit = {
    hostLog("sending exit")
    sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": 9, "method": "sbt/exec", "params": { "commandLine": "exit" } }"""
    )
    val deadline = 10.seconds.fromNow
    while (!deadline.isOverdue && process.isAlive) {
      Thread.sleep(10)
    }
    // We gave the server a chance to exit but it didn't within a reasonable time frame.
    if (deadline.isOverdue) process.destroy()
  }

  def sendJsonRpc(message: String): Unit = {
    writeLine(s"""Content-Length: ${message.size + 2}""")
    writeLine("")
    writeLine(message)
  }

  private def writeLine(s: String): Unit = {
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

  def readFrame: Future[Option[String]] = Future {
    def getContentLength: Int = {
      readLine map { line =>
        line.drop(16).toInt
      } getOrElse (0)
    }
    val l = getContentLength
    readLine
    readLine
    readContentLength(l)
  }

  final def waitForString(duration: FiniteDuration)(f: String => Boolean): Boolean = {
    val deadline = duration.fromNow
    def impl(): Boolean = {
      try {
        Await.result(readFrame, deadline.timeLeft).fold(false)(f) || impl
      } catch {
        case _: TimeoutException =>
          resetConnection() // create a new connection to invalidate the running readFrame future
          false
      }
    }
    impl()
  }

  final def neverReceive(duration: FiniteDuration)(f: String => Boolean): Boolean = {
    val deadline = duration.fromNow
    def impl(): Boolean = {
      try {
        Await.result(readFrame, deadline.timeLeft).fold(true)(s => !f(s)) && impl
      } catch {
        case _: TimeoutException =>
          resetConnection() // create a new connection to invalidate the running readFrame future
          true
      }
    }
    impl()
  }

  def readLine: Option[String] = {
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
      val chunk1 = if (chunk0.lastOption contains RetByte) chunk0.dropRight(1) else chunk0
      Some(new String(chunk1.toArray, "utf-8"))
    } else None // no EOL yet, so skip this turn.
  }

  def readContentLength(length: Int): Option[String] = {
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

}
