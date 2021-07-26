/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ File, IOException }
import java.net.Socket
import java.nio.file.{ Files, Path }
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean
import verify._
import sbt.{ ForkOptions, OutputStrategy, RunFromSourceMain }
import sbt.io.IO
import sbt.io.syntax._
import sbt.protocol.ClientSocket
import sjsonnew.JsonReader
import sjsonnew.support.scalajson.unsafe.{ Converter, Parser }

import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

trait AbstractServerTest extends TestSuite[Unit] {
  private var temp: File = _
  var svr: TestServer = _
  def testDirectory: String
  def testPath: Path = temp.toPath.resolve(testDirectory)

  private val targetDir: File = {
    val p0 = new File("..").getAbsoluteFile.getCanonicalFile / "target"
    val p1 = new File("target").getAbsoluteFile
    if (p0.exists) p0
    else p1
  }

  override def setupSuite(): Unit = {
    val base = Files.createTempDirectory(
      Files.createDirectories(targetDir.toPath.resolve("test-server")),
      "server-test"
    )
    temp = base.toFile
    val classpath = TestProperties.classpath.split(File.pathSeparator).map(new File(_))
    val sbtVersion = TestProperties.version
    val scalaVersion = TestProperties.scalaVersion
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
        testServer.waitForString(10.seconds) { s =>
          println(s)
          s contains """"capabilities":{""""
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
        testServer.waitForString(10.seconds) { s =>
          if (s.nonEmpty) println(s)
          s contains """"capabilities":{""""
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
  import TestServer.hostLog

  hostLog("fork to a new sbt instance")
  val forkOptions =
    ForkOptions()
      .withOutputStrategy(OutputStrategy.StdoutOutput)
      .withRunJVMOptions(Vector("-Djline.terminal=none", "-Dsbt.io.virtual=false"))
  val process =
    RunFromSourceMain.fork(forkOptions, baseDirectory, scalaVersion, sbtVersion, classpath)

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
      Thread.sleep(10) // Don't spam the portfile
    }
    if (deadline.isOverdue) sys.error(s"Timeout. $portfile is not found.")
    if (!process.isAlive) sys.error(s"Server unexpectedly terminated.")
  }
  private val waitDuration: FiniteDuration = 1.minute
  hostLog(s"wait $waitDuration until the server is ready to respond")
  waitForPortfile(waitDuration)

  @tailrec
  private def connect(attempt: Int): Socket = {
    val res = try Some(ClientSocket.socket(portfile)._1)
    catch { case _: IOException if attempt < 10 => None }
    res match {
      case Some(s) => s
      case _ =>
        Thread.sleep(100)
        connect(attempt + 1)
    }
  }
  // make connection to the socket described in the portfile
  val sk = connect(0)
  val out = sk.getOutputStream
  val in = sk.getInputStream
  private val lines = new LinkedBlockingQueue[String]
  val running = new AtomicBoolean(true)
  val readThread =
    new Thread(() => {
      while (running.get) {
        try lines.put(sbt.ReadJson(in, running))
        catch { case _: Exception => running.set(false) }
      }
    }, "sbt-server-test-read-thread") {
      setDaemon(true)
      start()
    }

  // initiate handshake
  sendJsonRpc(
    s"""{ "jsonrpc": "2.0", "id": 1, "method": "initialize", "params": { "initializationOptions": { "skipAnalysis": true } } }"""
  )

  def test(f: TestServer => Future[Assertion]): Future[Assertion] = {
    f(this)
  }

  def bye(): Unit =
    try {
      running.set(false)
      hostLog("sending exit")
      sendJsonRpc(
        """{ "jsonrpc": "2.0", "id": 9, "method": "sbt/exec", "params": { "commandLine": "shutdown" } }"""
      )
      val deadline = 5.seconds.fromNow
      while (!deadline.isOverdue && process.isAlive) {
        Thread.sleep(10)
      }
      // We gave the server a chance to exit but it didn't within a reasonable time frame.
      if (deadline.isOverdue && process.isAlive) {
        process.destroy()
        val newDeadline = 10.seconds.fromNow
        while (!newDeadline.isOverdue && process.isAlive) {
          Thread.sleep(10)
        }
      }
      if (process.isAlive) throw new IllegalStateException(s"process $process failed to exit")
    } finally {
      readThread.interrupt()
      /*
       * The UnixDomainSocket input stream cannot be closed while a thread is
       * reading from it (even if the UnixDomainSocket itself is closed):
       * https://github.com/sbt/ipcsocket/blob/f02d29092f9f0c57e5c4b276a31fa16975ddf66e/src/main/java/org/scalasbt/ipcsocket/UnixDomainSocket.java#L111-L118
       * This makes it impossible to interrupt the readThread until after the
       * server process has exited which closes the ServerSocket which does
       * cause the input stream to be closed. We could change the behavior of
       * ipcsocket, but that seems risky without knowing exactly why the behavior
       * exists. For now, ensure that we are able to interrupt and join the
       * read thread and throw an exception if not.
       */
      readThread.join(5000)
      if (readThread.isAlive) throw new IllegalStateException(s"Unable to join read thread")
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

  final def waitForString(duration: FiniteDuration)(f: String => Boolean): Boolean = {
    val deadline = duration.fromNow
    @tailrec def impl(): Boolean =
      lines.poll(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS) match {
        case null => false
        case s    => if (!f(s) && !deadline.isOverdue) impl() else !deadline.isOverdue()
      }
    impl()
  }
  final def waitFor[T: JsonReader](duration: FiniteDuration): T = {
    val deadline = duration.fromNow
    var lastEx: Throwable = null
    @tailrec def impl(): T =
      lines.poll(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS) match {
        case null =>
          if (lastEx != null) throw lastEx
          else throw new TimeoutException
        case s =>
          Parser
            .parseFromString(s)
            .flatMap(
              jvalue =>
                Converter.fromJson[T](
                  jvalue.toStandard
                    .asInstanceOf[sjsonnew.shaded.scalajson.ast.JObject]
                    .value("result")
                    .toUnsafe
                )
            ) match {
            case Success(value) =>
              value
            case Failure(exception) =>
              if (deadline.isOverdue) {
                val ex = new TimeoutException()
                ex.initCause(exception)
                throw ex
              } else {
                lastEx = exception
                impl()
              }
          }
      }
    impl()
  }
  final def waitForResponse(duration: FiniteDuration, id: Int): String = {
    val deadline = duration.fromNow
    @tailrec def impl(): String =
      lines.poll(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS) match {
        case null =>
          throw new TimeoutException()
        case s =>
          val s1 = s
          val correctId = s1.contains("\"id\":\"" + id + "\"")
          if (!correctId && !deadline.isOverdue) impl()
          else if (deadline.isOverdue)
            throw new TimeoutException()
          else s
      }
    impl()
  }

  final def neverReceive(duration: FiniteDuration)(f: String => Boolean): Boolean = {
    val deadline = duration.fromNow
    @tailrec
    def impl(): Boolean =
      lines.poll(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS) match {
        case null => true
        case s    => if (!f(s)) impl() else false
      }
    impl()
  }

}
