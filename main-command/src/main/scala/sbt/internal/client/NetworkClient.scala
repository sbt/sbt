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
import java.lang.ProcessBuilder.Redirect
import java.net.Socket
import java.nio.channels.ClosedChannelException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import java.util.concurrent.{ ConcurrentHashMap, LinkedBlockingQueue, TimeUnit }

import sbt.internal.client.NetworkClient.Arguments
import sbt.internal.langserver.{ LogMessageParams, MessageType, PublishDiagnosticsParams }
import sbt.internal.protocol._
import sbt.internal.util.{ ConsoleAppender, ConsoleOut, Signals, Terminal, Util }
import sbt.io.IO
import sbt.io.syntax._
import sbt.protocol._
import sbt.util.Level
import sjsonnew.BasicJsonProtocol._
import sjsonnew.shaded.scalajson.ast.unsafe.{ JObject, JValue }
import sjsonnew.support.scalajson.unsafe.Converter

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{ Failure, Properties, Success }
import Serialization.{
  CancelAll,
  attach,
  cancelRequest,
  systemIn,
  systemOut,
  terminalCapabilities,
  terminalCapabilitiesResponse,
  terminalPropertiesQuery,
  terminalPropertiesResponse
}
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
  private val pendingResults = new ConcurrentHashMap[String, (LinkedBlockingQueue[Integer], Long)]
  private val pendingCancellations = new ConcurrentHashMap[String, LinkedBlockingQueue[Boolean]]
  private val pendingCompletions = new ConcurrentHashMap[String, CompletionResponse => Unit]
  private val attached = new AtomicBoolean(false)
  private val attachUUID = new AtomicReference[String](null)
  private val connectionHolder = new AtomicReference[ServerConnection]
  private val batchMode = new AtomicBoolean(false)
  private val interactiveThread = new AtomicReference[Thread](null)
  private def mkSocket(file: File): (Socket, Option[String]) = ClientSocket.socket(file, useJNI)

  private def portfile = arguments.baseDirectory / "project" / "target" / "active.json"

  def connection: ServerConnection = connectionHolder.synchronized {
    connectionHolder.get match {
      case null => init(true)
      case c    => c
    }
  }

  private[this] val stdinBytes = new LinkedBlockingQueue[Int]
  private[this] val stdin: InputStream = new InputStream {
    override def available(): Int = stdinBytes.size
    override def read: Int = stdinBytes.take
  }
  private[this] val inputThread = new AtomicReference(new RawInputThread)
  private[this] val exitClean = new AtomicBoolean(true)
  private[this] val sbtProcess = new AtomicReference[Process](null)
  private class ConnectionRefusedException(t: Throwable) extends Throwable(t)

  // Open server connection based on the portfile
  def init(retry: Boolean): ServerConnection =
    try {
      if (!portfile.exists) {
        forkServer(portfile, log = true)
      }
      val (sk, tkn) =
        try mkSocket(portfile)
        catch { case e: IOException => throw new ConnectionRefusedException(e) }
      val conn = new ServerConnection(sk) {
        override def onNotification(msg: JsonRpcNotificationMessage): Unit =
          self.onNotification(msg)
        override def onRequest(msg: JsonRpcRequestMessage): Unit = self.onRequest(msg)
        override def onResponse(msg: JsonRpcResponseMessage): Unit = self.onResponse(msg)
        override def onShutdown(): Unit = {
          if (exitClean.get != false) exitClean.set(!running.get)
          running.set(false)
          Option(interactiveThread.get).foreach(_.interrupt())
        }
      }
      // initiate handshake
      val execId = UUID.randomUUID.toString
      val initCommand = InitCommand(tkn, Option(execId), Some(true))
      conn.sendString(Serialization.serializeCommandAsJsonMessage(initCommand))
      connectionHolder.set(conn)
      conn
    } catch {
      case e: ConnectionRefusedException if retry =>
        if (Files.deleteIfExists(portfile.toPath)) init(retry = false)
        else throw e
    }

  /**
   * Forks another instance of sbt in the background.
   * This instance must be shutdown explicitly via `sbt -client shutdown`
   */
  def forkServer(portfile: File, log: Boolean): Unit = {
    if (log) console.appendLog(Level.Info, "server was not detected. starting an instance")
    val color =
      if (!arguments.sbtArguments.exists(_.startsWith("-Dsbt.color=")))
        s"-Dsbt.color=${Terminal.console.isColorEnabled}" :: Nil
      else Nil
    val superShell =
      if (!arguments.sbtArguments.exists(_.startsWith("-Dsbt.supershell=")))
        s"-Dsbt.supershell=${Terminal.console.isColorEnabled}" :: Nil
      else Nil

    val args = color ++ superShell ++ arguments.sbtArguments
    val cmd = arguments.sbtScript +: args :+ BasicCommandStrings.CloseIOStreams
    val process =
      new ProcessBuilder(cmd: _*)
        .directory(arguments.baseDirectory)
        .redirectInput(Redirect.PIPE)
        .start()
    sbtProcess.set(process)
    val hook = new Thread(() => Option(sbtProcess.get).foreach(_.destroyForcibly()))
    Runtime.getRuntime.addShutdownHook(hook)
    val stdout = process.getInputStream
    val stderr = process.getErrorStream
    val stdin = process.getOutputStream
    @tailrec
    def blockUntilStart(): Unit = {
      val stop = try {
        while (stdout.available > 0) {
          val byte = stdout.read
          printStream.write(byte)
        }
        while (stderr.available > 0) {
          val byte = stderr.read
          errorStream.write(byte)
        }
        while (!stdinBytes.isEmpty) {
          stdin.write(stdinBytes.take)
          stdin.flush()
        }
        false
      } catch {
        case _: IOException => true
      }
      Thread.sleep(10)
      if (!portfile.exists && !stop) blockUntilStart()
      else {
        stdin.close()
        stdout.close()
        stderr.close()
        process.getOutputStream.close()
      }
    }

    try blockUntilStart()
    catch { case t: Throwable => t.printStackTrace() } finally {
      sbtProcess.set(null)
      Util.ignoreResult(Runtime.getRuntime.removeShutdownHook(hook))
    }
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

  private def getExitCode(jvalue: Option[JValue]): Integer = jvalue match {
    case Some(o: JObject) =>
      o.value
        .collectFirst {
          case v if v.field == "exitCode" =>
            Converter.fromJson[Integer](v.value).getOrElse(Integer.valueOf(1))
        }
        .getOrElse(1)
    case _ => 1
  }
  def onResponse(msg: JsonRpcResponseMessage): Unit = {
    pendingResults.remove(msg.id) match {
      case null =>
      case (q, startTime) =>
        val now = System.currentTimeMillis
        val message = timing(startTime, now)
        val exitCode = getExitCode(msg.result)
        if (batchMode.get || !attached.get) {
          if (exitCode == 0) console.success(message)
          else if (!attached.get) console.appendLog(Level.Error, message)
        }
        q.offer(exitCode)
    }
    pendingCancellations.remove(msg.id) match {
      case null =>
      case q    => q.offer(msg.toString.contains("Task cancelled"))
    }
    msg.id match {
      case execId =>
        if (attachUUID.get == msg.id) {
          attachUUID.set(null)
          attached.set(true)
          Option(inputThread.get).foreach(_.drain())
        }
        pendingCompletions.remove(execId) match {
          case null =>
          case completions =>
            completions(msg.result match {
              case Some(o: JObject) =>
                o.value
                  .foldLeft(CompletionResponse(Vector.empty[String])) {
                    case (resp, i) =>
                      if (i.field == "items")
                        resp.withItems(
                          Converter
                            .fromJson[Vector[String]](i.value)
                            .getOrElse(Vector.empty[String])
                        )
                      else resp
                  }
              case _ => CompletionResponse(Vector.empty[String])
            })
        }
    }
  }

  def onNotification(msg: JsonRpcNotificationMessage): Unit = {
    def splitToMessage: Vector[(Level.Value, String)] =
      (msg.method, msg.params) match {
        case ("build/logMessage", Some(json)) =>
          if (!attached.get) {
            import sbt.internal.langserver.codec.JsonProtocol._
            Converter.fromJson[LogMessageParams](json) match {
              case Success(params) => splitLogMessage(params)
              case Failure(_)      => Vector()
            }
          } else Vector()
        case (`systemOut`, Some(json)) =>
          Converter.fromJson[Seq[Byte]](json) match {
            case Success(params) =>
              if (params.nonEmpty) {
                if (attached.get) {
                  printStream.write(params.toArray)
                  printStream.flush()
                }
              }
            case Failure(_) =>
          }
          Vector.empty
        case ("textDocument/publishDiagnostics", Some(json)) =>
          import sbt.internal.langserver.codec.JsonProtocol._
          Converter.fromJson[PublishDiagnosticsParams](json) match {
            case Success(params) => splitDiagnostics(params); Vector()
            case Failure(_)      => Vector()
          }
        case ("shutdown", Some(_))                => Vector.empty
        case (msg, _) if msg.startsWith("build/") => Vector.empty
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
    (msg.method, msg.params) match {
      case (`terminalCapabilities`, Some(json)) =>
        import sbt.protocol.codec.JsonProtocol._
        Converter.fromJson[TerminalCapabilitiesQuery](json) match {
          case Success(terminalCapabilitiesQuery) =>
            val response = TerminalCapabilitiesResponse(
              terminalCapabilitiesQuery.boolean.map(Terminal.console.getBooleanCapability),
              terminalCapabilitiesQuery.numeric.map(Terminal.console.getNumericCapability),
              terminalCapabilitiesQuery.string
                .map(s => Option(Terminal.console.getStringCapability(s)).getOrElse("null")),
            )
            sendCommandResponse(
              terminalCapabilitiesResponse,
              response,
              msg.id,
            )
          case Failure(_) =>
        }
      case (`terminalPropertiesQuery`, _) =>
        val response = TerminalPropertiesResponse.apply(
          width = Terminal.console.getWidth,
          height = Terminal.console.getHeight,
          isAnsiSupported = Terminal.console.isAnsiSupported,
          isColorEnabled = Terminal.console.isColorEnabled,
          isSupershellEnabled = Terminal.console.isSupershellEnabled,
          isEchoEnabled = Terminal.console.isEchoEnabled
        )
        sendCommandResponse(terminalPropertiesResponse, response, msg.id)
      case _ =>
    }
  }

  def connect(log: Boolean): Unit = {
    if (log) console.appendLog(Level.Info, "entering *experimental* thin client - BEEP WHIRR")
    init(retry = true)
    ()
  }

  private[this] val contHandler: () => Unit = () => {
    if (Terminal.console.getLastLine.nonEmpty)
      printStream.print(ConsoleAppender.DeleteLine + Terminal.console.getLastLine.get)
  }
  private[this] def withSignalHandler[R](handler: () => Unit, sig: String)(f: => R): R = {
    val registration = Signals.register(handler, sig)
    try f
    finally registration.remove()
  }
  private[this] val cancelled = new AtomicBoolean(false)

  def run(): Int =
    withSignalHandler(contHandler, Signals.CONT) {
      interactiveThread.set(Thread.currentThread)
      val cleaned = arguments.commandArguments
      val userCommands = cleaned.takeWhile(_ != "exit")
      val interactive = cleaned.isEmpty
      val exit = cleaned.nonEmpty && userCommands.isEmpty
      attachUUID.set(sendJson(attach, s"""{"interactive": $interactive}"""))
      val handler: () => Unit = () => {
        def exitAbruptly() = {
          exitClean.set(false)
          close()
        }
        if (cancelled.compareAndSet(false, true)) {
          val cancelledTasks = {
            val queue = sendCancelAllCommand()
            Option(queue.poll(1, TimeUnit.SECONDS)).getOrElse(true)
          }
          if ((!interactive && pendingResults.isEmpty) || !cancelledTasks) exitAbruptly()
          else cancelled.set(false)
        } else exitAbruptly() // handles double ctrl+c to force a shutdown
      }
      withSignalHandler(handler, Signals.INT) {
        if (interactive) {
          try this.synchronized(this.wait)
          catch { case _: InterruptedException => }
          if (exitClean.get) 0 else 1
        } else if (exit) {
          0
        } else {
          batchMode.set(true)
          batchExecute(userCommands.toList)
        }
      }
    }

  def batchExecute(userCommands: List[String]): Int = {
    val cmd = userCommands mkString " "
    printStream.println("> " + cmd)
    sendAndWait(cmd, None)
  }

  private def sendAndWait(cmd: String, limit: Option[Deadline]): Int = {
    val queue = sendExecCommand(cmd)
    var result: Integer = null
    while (running.get && result == null && limit.fold(true)(!_.isOverdue())) {
      try {
        result = limit match {
          case Some(l) => queue.poll((l - Deadline.now).toMillis, TimeUnit.MILLISECONDS)
          case _       => queue.take
        }
      } catch {
        case _: InterruptedException if cmd == "shutdown" => result = 0
        case _: InterruptedException                      => result = if (exitClean.get) 0 else 1
      }
    }
    if (result == null) 1 else result
  }

  def sendExecCommand(commandLine: String): LinkedBlockingQueue[Integer] = {
    val execId = UUID.randomUUID.toString
    val queue = new LinkedBlockingQueue[Integer]
    sendCommand(ExecCommand(commandLine, execId))
    pendingResults.put(execId, (queue, System.currentTimeMillis))
    queue
  }

  def sendCancelAllCommand(): LinkedBlockingQueue[Boolean] = {
    val queue = new LinkedBlockingQueue[Boolean]
    val execId = sendJson(cancelRequest, s"""{"id":"$CancelAll"}""")
    pendingCancellations.put(execId, queue)
    queue
  }

  def sendCommand(command: CommandMessage): Unit = {
    try {
      val s = Serialization.serializeCommandAsJsonMessage(command)
      connection.sendString(s)
      lock.synchronized {
        status.set("Processing")
      }
    } catch {
      case e: IOException =>
        errorStream.println(s"Caught exception writing command to server: $e")
        running.set(false)
    }
  }
  def sendCommandResponse(method: String, command: EventMessage, id: String): Unit = {
    try {
      val s = new String(Serialization.serializeEventMessage(command))
      val msg = s"""{ "jsonrpc": "2.0", "id": "$id", "result": $s }"""
      connection.sendString(msg)
    } catch {
      case e: IOException =>
        errorStream.println(s"Caught exception writing command to server: $e")
        running.set(false)
    }
  }
  def sendJson(method: String, params: String): String = {
    val uuid = UUID.randomUUID.toString
    val msg = s"""{ "jsonrpc": "2.0", "id": "$uuid", "method": "$method", "params": $params }"""
    connection.sendString(msg)
    uuid
  }

  def sendNotification(method: String, params: String): Unit = {
    connection.sendString(s"""{ "jsonrpc": "2.0", "method": "$method", "params": $params }""")
  }

  override def close(): Unit =
    try {
      running.set(false)
      stdinBytes.offer(-1)
      val mainThread = interactiveThread.getAndSet(null)
      if (mainThread != null && mainThread != Thread.currentThread) mainThread.interrupt
      connection.shutdown()
      Option(inputThread.get).foreach(_.interrupt())
    } catch {
      case t: Throwable => t.printStackTrace(); throw t
    }

  private[this] class RawInputThread extends Thread("sbt-read-input-thread") with AutoCloseable {
    setDaemon(true)
    start()
    val stopped = new AtomicBoolean(false)
    val lock = new Object
    override final def run(): Unit = {
      @tailrec def read(): Unit = {
        inputStream.read match {
          case -1 =>
          case b =>
            lock.synchronized(stdinBytes.offer(b))
            if (attached.get()) drain()
            if (!stopped.get()) read()
        }
      }
      try Terminal.console.withRawSystemIn(read())
      catch { case _: InterruptedException | _: ClosedChannelException => stopped.set(true) }
    }

    def drain(): Unit = lock.synchronized {
      while (!stdinBytes.isEmpty) {
        val byte = stdinBytes.poll()
        sendNotification(systemIn, byte.toString)
      }
    }

    override def close(): Unit = {
      RawInputThread.this.interrupt()
    }
  }

  // copied from Aggregation
  private def timing(startTime: Long, endTime: Long): String = {
    import java.text.DateFormat
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
    val nowString = format.format(new java.util.Date(endTime))
    val total = math.max(0, (endTime - startTime + 500) / 1000)
    val totalString = s"$total s" +
      (if (total <= 60) ""
       else {
         val maybeHours = total / 3600 match {
           case 0 => ""
           case h => f"$h%02d:"
         }
         val mins = f"${total % 3600 / 60}%02d"
         val secs = f"${total % 60}%02d"
         s" ($maybeHours$mins:$secs)"
       })
    s"Total time: $totalString, completed $nowString"
  }
}

object NetworkClient {
  private[sbt] val CancelAll = "__CancelAll"
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

  def client(
      baseDirectory: File,
      args: Array[String],
      inputStream: InputStream,
      errorStream: PrintStream,
      printStream: PrintStream,
      useJNI: Boolean
  ): Int = {
    val client =
      simpleClient(
        NetworkClient.parseArgs(args).withBaseDirectory(baseDirectory),
        inputStream,
        errorStream,
        printStream,
        useJNI,
      )
    try {
      client.connect(log = true)
      client.run()
    } catch { case _: Exception => 1 } finally client.close()
  }
  private def simpleClient(
      arguments: Arguments,
      inputStream: InputStream,
      errorStream: PrintStream,
      printStream: PrintStream,
      useJNI: Boolean,
  ): NetworkClient =
    new NetworkClient(
      NetworkClient.simpleConsoleInterface(printStream),
      arguments,
      inputStream,
      errorStream,
      printStream,
      useJNI
    )
  def main(args: Array[String]): Unit = {
    val (jnaArg, restOfArgs) = args.partition(_ == "--jna")
    val useJNI = jnaArg.isEmpty
    val hook = new Thread(() => {
      System.out.print(ConsoleAppender.ClearScreenAfterCursor)
      System.out.flush()
    })
    Runtime.getRuntime.addShutdownHook(hook)
    System.exit(Terminal.withStreams {
      val base = new File("").getCanonicalFile()
      try client(base, restOfArgs, System.in, System.err, System.out, useJNI)
      finally {
        Runtime.getRuntime.removeShutdownHook(hook)
        hook.run()
      }
    })
  }

  def run(configuration: xsbti.AppConfiguration, arguments: List[String]): Int =
    try {
      val client = new NetworkClient(configuration, parseArgs(arguments.toArray))
      try {
        client.connect(log = true)
        client.run()
      } catch { case _: Throwable => 1 } finally client.close()
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        1
    }
}
