/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package client

import java.io.{ File, IOException, InputStream, PrintStream }
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

import sbt.internal.langserver.{ LogMessageParams, MessageType, PublishDiagnosticsParams }
import sbt.internal.protocol._
import sbt.internal.util.{ ConsoleAppender, ConsoleOut, LineReader }
import sbt.io.IO
import sbt.io.syntax._
import sbt.protocol._
import sbt.util.Level
import sjsonnew.support.scalajson.unsafe.Converter

import scala.collection.mutable.ListBuffer
import scala.collection.mutable
import scala.sys.process.{ BasicIO, Process, ProcessLogger }
import scala.util.Properties
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }
import NetworkClient.Arguments

trait ConsoleInterface {
  def appendLog(level: Level.Value, message: => String): Unit
  def success(msg: String): Unit
}

class NetworkClient(
    console: ConsoleInterface,
    arguments: Arguments,
    inputStream: InputStream,
    errorStream: PrintStream,
    printStream: PrintStream,
    useJNI: Boolean,
) extends AutoCloseable { self =>
  def this(configuration: xsbti.AppConfiguration, arguments: Arguments) =
    this(
      console = NetworkClient.consoleAppenderInterface(System.out),
      arguments = arguments.withBaseDirectory(configuration.baseDirectory),
      inputStream = System.in,
      errorStream = System.err,
      printStream = System.out,
      useJNI = false,
    )
  def this(configuration: xsbti.AppConfiguration, args: List[String]) =
    this(
      console = NetworkClient.consoleAppenderInterface(System.out),
      arguments =
        NetworkClient.parseArgs(args.toArray).withBaseDirectory(configuration.baseDirectory),
      inputStream = System.in,
      errorStream = System.err,
      printStream = System.out,
      useJNI = false,
    )
  private val status = new AtomicReference("Ready")
  private val lock: AnyRef = new AnyRef {}
  private val running = new AtomicBoolean(true)
  private val pendingExecIds = ListBuffer.empty[String]

  private def baseDirectory: File = arguments.baseDirectory

  lazy val connection = init()

  start()

  // Open server connection based on the portfile
  def init(): ServerConnection = {
    val portfile = baseDirectory / "project" / "target" / "active.json"
    if (!portfile.exists) {
      forkServer(portfile)
    }
    val (sk, tkn) = ClientSocket.socket(portfile)
    val conn = new ServerConnection(sk) {
      override def onNotification(msg: JsonRpcNotificationMessage): Unit = self.onNotification(msg)
      override def onRequest(msg: JsonRpcRequestMessage): Unit = self.onRequest(msg)
      override def onResponse(msg: JsonRpcResponseMessage): Unit = self.onResponse(msg)
      override def onShutdown(): Unit = {
        running.set(false)
      }
    }
    // initiate handshake
    val execId = UUID.randomUUID.toString
    val initCommand = InitCommand(tkn, Option(execId), Some(true))
    conn.sendString(Serialization.serializeCommandAsJsonMessage(initCommand))
    conn
  }

  /**
   * Forks another instance of sbt in the background.
   * This instance must be shutdown explicitly via `sbt -client shutdown`
   */
  def forkServer(portfile: File): Unit = {
    console.appendLog(Level.Info, "server was not detected. starting an instance")
    val args = List[String]()
    val launchOpts = List("-Xms2048M", "-Xmx2048M", "-Xss2M")
    val launcherJarString = sys.props.get("java.class.path") match {
      case Some(cp) =>
        cp.split(File.pathSeparator)
          .toList
          .headOption
          .getOrElse(sys.error("launcher JAR classpath not found"))
      case _ => sys.error("property java.class.path expected")
    }
    val cmd = "java" :: launchOpts ::: "-jar" :: launcherJarString :: args
    // val cmd = "sbt"
    val io = BasicIO(false, ProcessLogger(_ => ()))
    val _ = Process(cmd, baseDirectory).run(io)
    def waitForPortfile(n: Int): Unit =
      if (portfile.exists) {
        console.appendLog(Level.Info, "server found")
      } else {
        if (n <= 0) sys.error(s"timeout. $portfile is not found.")
        else {
          Thread.sleep(1000)
          if ((n - 1) % 10 == 0) {
            console.appendLog(Level.Info, "waiting for the server...")
          }
          waitForPortfile(n - 1)
        }
      }
    waitForPortfile(90)
  }

  /** Called on the response for a returning message. */
  def onReturningReponse(msg: JsonRpcResponseMessage): Unit = {
    def printResponse(): Unit = {
      msg.result match {
        case Some(result) =>
          // ignore result JSON
          console.success("completed")
        case _ =>
          msg.error match {
            case Some(err) =>
              // ignore err details
              console.appendLog(Level.Error, "completed")
            case _ => // ignore
          }
      }
    }
    printResponse()
  }

  def onResponse(msg: JsonRpcResponseMessage): Unit = {
    msg.id match {
      case execId if pendingExecIds contains execId =>
        onReturningReponse(msg)
        lock.synchronized {
          pendingExecIds -= execId
        }
        ()
      case _ =>
    }
  }

  def onNotification(msg: JsonRpcNotificationMessage): Unit = {
    def splitToMessage: Vector[(Level.Value, String)] =
      (msg.method, msg.params) match {
        case ("build/logMessage", Some(json)) =>
          import sbt.internal.langserver.codec.JsonProtocol._
          Converter.fromJson[LogMessageParams](json) match {
            case Success(params) => splitLogMessage(params)
            case Failure(e)      => Vector()
          }
        case ("textDocument/publishDiagnostics", Some(json)) =>
          import sbt.internal.langserver.codec.JsonProtocol._
          Converter.fromJson[PublishDiagnosticsParams](json) match {
            case Success(params) => splitDiagnostics(params)
            case Failure(e)      => Vector()
          }
        case _ =>
          Vector(
            (
              Level.Warn,
              s"unknown event: ${msg.method} " + Serialization.compactPrintJsonOpt(msg.params)
            )
          )
      }
    splitToMessage foreach {
      case (level, msg) => console.appendLog(level, msg)
    }
  }

  def splitLogMessage(params: LogMessageParams): Vector[(Level.Value, String)] = {
    val level = messageTypeToLevel(params.`type`)
    if (level == Level.Debug) Vector()
    else Vector((level, params.message))
  }

  def messageTypeToLevel(severity: Long): Level.Value = {
    severity match {
      case MessageType.Error   => Level.Error
      case MessageType.Warning => Level.Warn
      case MessageType.Info    => Level.Info
      case MessageType.Log     => Level.Debug
    }
  }

  def splitDiagnostics(params: PublishDiagnosticsParams): Vector[(Level.Value, String)] = {
    val uri = new URI(params.uri)
    val f = IO.toFile(uri)

    params.diagnostics map { d =>
      val level = d.severity match {
        case Some(severity) => messageTypeToLevel(severity)
        case _              => Level.Error
      }
      val line = d.range.start.line + 1
      val offset = d.range.start.character + 1
      val msg = s"$f:$line:$offset: ${d.message}"
      (level, msg)
    }
  }

  def onRequest(msg: JsonRpcRequestMessage): Unit = {
    // ignore
  }

  def start(): Unit = {
    console.appendLog(Level.Info, "entering *experimental* thin client - BEEP WHIRR")
    val _ = connection
    val userCommands = arguments.commandArguments.toList
    if (userCommands.isEmpty) shell()
    else batchExecute(userCommands)
  }

  def batchExecute(userCommands: List[String]): Unit = {
    userCommands foreach { cmd =>
      println("> " + cmd)
      val execId =
        if (cmd == "shutdown") sendExecCommand("exit")
        else sendExecCommand(cmd)
      while (pendingExecIds contains execId) {
        Thread.sleep(100)
      }
    }
  }

  def shell(): Unit = {
    val reader = LineReader.simple(None, LineReader.HandleCONT, injectThreadSleep = true)
    while (running.get) {
      reader.readLine("> ", None) match {
        case Some("shutdown") =>
          // `sbt -client shutdown` shuts down the server
          sendExecCommand("exit")
          Thread.sleep(100)
          running.set(false)
        case Some("exit") =>
          running.set(false)
        case Some(s) if s.trim.nonEmpty =>
          val execId = sendExecCommand(s)
          while (pendingExecIds contains execId) {
            Thread.sleep(100)
          }
        case _ => //
      }
    }
  }

  def sendExecCommand(commandLine: String): String = {
    val execId = UUID.randomUUID.toString
    sendCommand(ExecCommand(commandLine, execId))
    lock.synchronized {
      pendingExecIds += execId
    }
    execId
  }

  def sendCommand(command: CommandMessage): Unit = {
    try {
      val s = Serialization.serializeCommandAsJsonMessage(command)
      connection.sendString(s)
    } catch {
      case _: IOException =>
      // log.debug(e.getMessage)
      // toDel += client
    }
    lock.synchronized {
      status.set("Processing")
    }
  }
  override def close(): Unit = {}
}
object NetworkClient {
  private def consoleAppenderInterface(printStream: PrintStream): ConsoleInterface = {
    val appender = ConsoleAppender("thin", ConsoleOut.printStreamOut(printStream))
    new ConsoleInterface {
      override def appendLog(level: Level.Value, message: => String): Unit =
        appender.appendLog(level, message)
      override def success(msg: String): Unit = appender.success(msg)
    }
  }
  private def simpleConsoleInterface(printStream: PrintStream): ConsoleInterface =
    new ConsoleInterface {
      import scala.Console.{ GREEN, RED, RESET, YELLOW }
      override def appendLog(level: Level.Value, message: => String): Unit = {
        val prefix = level match {
          case Level.Error => s"[$RED$level$RESET]"
          case Level.Warn  => s"[$YELLOW$level$RESET]"
          case _           => s"[$RESET$level$RESET]"
        }
        message.split("\n").foreach { line =>
          if (!line.trim.isEmpty) printStream.println(s"$prefix $line")
        }
      }
      override def success(msg: String): Unit = printStream.println(s"[${GREEN}success$RESET] $msg")
    }
  private[client] class Arguments(
      val baseDirectory: File,
      val sbtArguments: Seq[String],
      val commandArguments: Seq[String],
      val sbtScript: String,
  ) {
    def withBaseDirectory(file: File): Arguments =
      new Arguments(file, sbtArguments, commandArguments, sbtScript)
  }
  private[client] def parseArgs(args: Array[String]): Arguments = {
    var i = 0
    var sbtScript = if (Properties.isWin) "sbt.cmd" else "sbt"
    val commandArgs = new mutable.ArrayBuffer[String]
    val sbtArguments = new mutable.ArrayBuffer[String]
    val SysProp = "-D([^=]+)=(.*)".r
    val sanitized = args.flatMap {
      case a if a.startsWith("\"") => Array(a)
      case a                       => a.split(" ")
    }
    while (i < sanitized.length) {
      sanitized(i) match {
        case a if a.startsWith("--sbt-script=") =>
          sbtScript = a.split("--sbt-script=").lastOption.getOrElse(sbtScript)
        case a if !a.startsWith("-") => commandArgs += a
        case a @ SysProp(key, value) =>
          System.setProperty(key, value)
          sbtArguments += a
        case a =>
          sbtArguments += a
      }
      i += 1
    }
    new Arguments(new File("").getCanonicalFile, sbtArguments, commandArgs, sbtScript)
  }

  def run(configuration: xsbti.AppConfiguration, arguments: List[String]): Int =
    try {
      val client = new NetworkClient(configuration, parseArgs(arguments.toArray))
      try {
        client.start()
        0
      } catch { case _: Throwable => 1 } finally client.close()
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        1
    }
}
