/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import org.scalasbt.shadedgson.com.google.gson.Gson
import java.io.*
import java.net.{ InetAddress, ServerSocket, StandardProtocolFamily, UnixDomainSocketAddress }
import java.nio.channels.{ ServerSocketChannel, SocketChannel }
import java.nio.file.{ Files, Path as NioPath }
import java.util.Scanner
import sbt.io.IO
import sbt.internal.io.Retry
import sbt.internal.worker1.*
import sbt.protocol.DuplexChannels
import sbt.testing.Framework
import scala.sys.process.{ BasicIO, Process, ProcessIO }
import scala.collection.mutable
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration.*
import scala.util.control.NonFatal

object WorkerExchange:
  val listeners: mutable.ListBuffer[WorkerResponseListener] = ListBuffer.empty
  private val loopback = InetAddress.getByName(null)
  private val jdkIpcSupportCache = TrieMap.empty[Option[File], Boolean]

  /**
   * Start a worker process.
   */
  def startWorker(fo: ForkOptions, extraCp: Seq[File]): WorkerProxy =
    val ct =
      if supportsUnixDomainSockets(fo.javaHome) then WorkerConnection.Ipc(newIpcSocketPath())
      else WorkerConnection.Stdio
    startWorker(fo, extraCp, ct)

  /**
   * True if `javaHome` (None meaning the JDK currently running sbt) is JDK 16+.
   */
  private def supportsUnixDomainSockets(javaHome: Option[File]): Boolean =
    def doDetect: Boolean =
      javaHome match
        case None       => true // the JDK running sbt itself, which requires 17+
        case Some(home) =>
          try
            val releaseFile = File(home, "release")
            val props = java.util.Properties()
            val in = FileInputStream(releaseFile)
            try props.load(in)
            finally in.close()
            val raw = Option(props.getProperty("JAVA_VERSION")).getOrElse("")
            val version = raw.stripPrefix("\"").stripSuffix("\"")
            val digits =
              version.takeWhile(c => c.isDigit || c == '.').split('.').flatMap(_.toIntOption)
            val major = digits match
              case Array(1, minor, _*) => minor // legacy 1.8-style versioning
              case Array(m, _*)        => m
              case _                   => 0
            major >= 16
          catch case NonFatal(_) => false
    jdkIpcSupportCache.getOrElseUpdate(javaHome, doDetect)

  /**
   * Start a worker process.
   */
  def startWorker(
      fo: ForkOptions,
      extraCp: Seq[File],
      connectionType: WorkerConnection,
  ): WorkerProxy =
    // put extraCp first so we can shadow the WorkerMain class
    val fullCp = extraCp ++ Seq(
      IO.classLocationPath(classOf[WorkerMain]).toFile,
      IO.classLocationPath(classOf[Framework]).toFile,
      IO.classLocationPath(classOf[Gson]).toFile,
    )
    val inputRef = Promise[OutputStream]()
    def runAccepter(out: OutputStream, in: InputStream): Unit =
      inputRef.success(out)
      val scanner = Scanner(in, "UTF-8")
      while scanner.hasNextLine() do notifyListeners(scanner.nextLine())
    val (connArgs, closer) = connectionType match
      case WorkerConnection.Tcp =>
        val serverSocket = Retry(ServerSocket(0, 1, loopback))
        val accepter = Thread(() => {
          val socket = serverSocket.accept()
          runAccepter(socket.getOutputStream(), socket.getInputStream())
        })
        accepter.setName("sbt-fork-test-response-reader")
        accepter.setPriority(Thread.NORM_PRIORITY + 1)
        accepter.start()
        (Seq("--tcp", serverSocket.getLocalPort().toString()), Some(serverSocket))
      case WorkerConnection.Ipc(path) =>
        val serverChannel = Retry {
          Files.deleteIfExists(path)
          val ch = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
          ch.bind(UnixDomainSocketAddress.of(path))
          ch
        }
        @volatile var acceptedChannel: SocketChannel = null
        val accepter = Thread(() => {
          val channel = serverChannel.accept()
          acceptedChannel = channel
          runAccepter(
            DuplexChannels.newOutputStream(channel),
            DuplexChannels.newInputStream(channel)
          )
        })
        accepter.setName("sbt-fork-test-response-reader")
        accepter.setPriority(Thread.NORM_PRIORITY + 1)
        accepter.start()
        val closer: AutoCloseable = () => {
          if acceptedChannel != null then acceptedChannel.close()
          serverChannel.close()
          Files.deleteIfExists(path)
        }
        (Seq("--ipc", path.toString()), Some(closer))
      case WorkerConnection.Stdio => (Nil, None)
    val options = Seq(
      "-classpath",
      fullCp.mkString(File.pathSeparator),
      classOf[WorkerMain].getCanonicalName,
    ) ++ connArgs
    val onStdoutLine: String => Unit = connectionType match
      case WorkerConnection.Stdio => notifyListeners
      case _                      => (line) => scala.Console.out.println(line)
    val processIo = ProcessIO(
      in = (input) =>
        (connectionType match
          case WorkerConnection.Stdio => inputRef.success(input)
          case _                      => ()
        ),
      out = BasicIO.processFully(onStdoutLine),
      err = BasicIO.processFully((line) => scala.Console.err.println(line)),
    )
    val forkWithIo = fo.withOutputStrategy(OutputStrategy.CustomInputOutput(processIo))
    val p = Fork.java.fork(forkWithIo, options)
    val forkTimeout = fo.connectionTimeout.getOrElse(30.seconds)
    val input = Await.result(inputRef.future, forkTimeout)
    WorkerProxy(input, p, options, closer)

  /** Generates a fresh path suitable for binding a `WorkerConnection.Ipc` socket. */
  def newIpcSocketPath(): NioPath =
    val dir = NioPath
      .of(sys.env.getOrElse("XDG_RUNTIME_DIR", sys.props("java.io.tmpdir")))
      .resolve(".sbt-fork-ipc")
    Files.createDirectories(dir)
    val path = Files.createTempFile(dir, "fork-", ".sock")
    Files.deleteIfExists(path)
    path

  def registerListener(listener: WorkerResponseListener): Unit =
    synchronized:
      listeners.append(listener)

  def unregisterListener(listener: WorkerResponseListener): Unit =
    synchronized:
      if listeners.contains(listener) then listeners.remove(listeners.indexOf(listener))
      else ()

  /**
   * Unified worker output handler.
   */
  def notifyListeners(line: String): Unit =
    synchronized:
      listeners.foreach: wl =>
        wl(line)
end WorkerExchange

class WorkerProxy(
    input: OutputStream,
    val process: Process,
    val options: Seq[String],
    closer: Option[AutoCloseable],
) extends AutoCloseable:
  lazy val inputStream = PrintStream(input)
  def close(): Unit =
    input.close()
    closer.foreach(_.close())
  def blockForExitCode(): Int =
    if !process.isAlive() then process.exitValue()
    else Fork.blockForExitCode(process)

  /** print a line into stdin of the worker process. */
  def println(str: String): Unit =
    inputStream.println(str)
    inputStream.flush()

  val watch = Thread(() => {
    while process.isAlive() do Thread.sleep(100)
    WorkerExchange.listeners.foreach(_.notifyExit(process))
  })
  watch.start()
end WorkerProxy

abstract class WorkerResponseListener extends Function1[String, Unit]:
  def notifyExit(p: Process): Unit

enum WorkerConnection:
  case Stdio
  case Tcp
  case Ipc(path: NioPath)
