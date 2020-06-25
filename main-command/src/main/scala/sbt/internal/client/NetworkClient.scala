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
import scala.util.{ Failure, Properties, Success, Try }
import Serialization.{
  CancelAll,
  attach,
  cancelRequest,
  promptChannel,
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
  private lazy val noTab = arguments.completionArguments.contains("--no-tab")
  private lazy val noStdErr = arguments.completionArguments.contains("--no-stderr") &&
    System.getenv("SBTC_AUTO_COMPLETE") == null

  private def mkSocket(file: File): (Socket, Option[String]) = ClientSocket.socket(file, useJNI)

  private def portfile = arguments.baseDirectory / "project" / "target" / "active.json"

  def connection: ServerConnection = connectionHolder.synchronized {
    connectionHolder.get match {
      case null => init(prompt = false, retry = true)
      case c    => c
    }
  }

  private[this] val stdinBytes = new LinkedBlockingQueue[Int]
  private[this] val inputThread = new AtomicReference(new RawInputThread)
  private[this] val exitClean = new AtomicBoolean(true)
  private[this] val sbtProcess = new AtomicReference[Process](null)
  private class ConnectionRefusedException(t: Throwable) extends Throwable(t)
  private class ServerFailedException extends Exception

  // Open server connection based on the portfile
  def init(prompt: Boolean, retry: Boolean): ServerConnection =
    try {
      if (!portfile.exists) {
        if (prompt) {
          val msg = if (noTab) "" else "No sbt server is running. Press <tab> to start one..."
          errorStream.print(s"\n$msg")
          if (noStdErr) System.exit(0)
          else if (noTab) forkServer(portfile, log = true)
          else {
            stdinBytes.take match {
              case 9 =>
                errorStream.println("\nStarting server...")
                forkServer(portfile, !prompt)
              case _ => System.exit(0)
            }
          }
        } else {
          forkServer(portfile, log = true)
        }
      }
      @tailrec def connect(attempt: Int): (Socket, Option[String]) = {
        val res = try Some(mkSocket(portfile))
        catch {
          // This catches a pipe busy exception which can happen if two windows clients
          // attempt to connect in rapid succession
          case e: IOException if e.getMessage.contains("Couldn't open") && attempt < 10 => None
          case e: IOException                                                           => throw new ConnectionRefusedException(e)
        }
        res match {
          case Some(r) => r
          case None    =>
            // Use a random sleep to spread out the competing processes
            Thread.sleep(new java.util.Random().nextInt(20).toLong)
            connect(attempt + 1)
        }
      }
      val (sk, tkn) = connect(0)
      val conn = new ServerConnection(sk) {
        override def onNotification(msg: JsonRpcNotificationMessage): Unit = {
          msg.method match {
            case "shutdown" =>
              val log = msg.params match {
                case Some(jvalue) => Converter.fromJson[Boolean](jvalue).getOrElse(true)
                case _            => false
              }
              if (running.compareAndSet(true, false) && log) {
                if (!arguments.commandArguments.contains("shutdown")) {
                  if (Terminal.console.getLastLine.fold(true)(_.nonEmpty)) errorStream.println()
                  console.appendLog(Level.Error, "sbt server disconnected")
                  exitClean.set(false)
                }
              }
              stdinBytes.offer(-1)
              Option(inputThread.get).foreach(_.close())
              Option(interactiveThread.get).foreach(_.interrupt)
            case "readInput" =>
            case _           => self.onNotification(msg)
          }
        }
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
        if (Files.deleteIfExists(portfile.toPath)) init(prompt, retry = false)
        else throw e
    }

  /**
   * Forks another instance of sbt in the background.
   * This instance must be shutdown explicitly via `sbt -client shutdown`
   */
  def forkServer(portfile: File, log: Boolean): Unit = {
    val bootSocketName =
      BootServerSocket.socketLocation(arguments.baseDirectory.toPath.toRealPath())
    var socket: Option[Socket] = Try(ClientSocket.localSocket(bootSocketName, useJNI)).toOption
    val process = socket match {
      case None =>
        val term = Terminal.console
        if (log) console.appendLog(Level.Info, "server was not detected. starting an instance")
        val props =
          Seq(
            term.getWidth,
            term.getHeight,
            term.isAnsiSupported,
            term.isColorEnabled,
            term.isSupershellEnabled
          ).mkString(",")

        val cmd = arguments.sbtScript +: arguments.sbtArguments :+ BasicCommandStrings.CloseIOStreams
        val processBuilder =
          new ProcessBuilder(cmd: _*)
            .directory(arguments.baseDirectory)
            .redirectInput(Redirect.PIPE)
        processBuilder.environment.put(Terminal.TERMINAL_PROPS, props)
        val process = processBuilder.start()
        sbtProcess.set(process)
        Some(process)
      case _ =>
        if (log) console.appendLog(Level.Info, "sbt server is booting up")
        None
    }
    val hook = new Thread(() => Option(sbtProcess.get).foreach(_.destroyForcibly()))
    Runtime.getRuntime.addShutdownHook(hook)
    val isWin = Properties.isWin
    var gotInputBack = false
    val readThreadAlive = new AtomicBoolean(true)
    /*
     * Socket.getInputStream.available doesn't always return a value greater than 0
     * so it is necessary to read the process output from the socket on a background
     * thread.
     */
    val readThread = new Thread("client-read-thread") {
      setDaemon(true)
      start()
      override def run(): Unit = {
        try {
          while (readThreadAlive.get) {
            socket.foreach { s =>
              try {
                s.getInputStream.read match {
                  case -1 | 0            => readThreadAlive.set(false)
                  case 2                 => gotInputBack = true
                  case 3 if gotInputBack => readThreadAlive.set(false)
                  case i if gotInputBack => stdinBytes.offer(i)
                  case i                 => printStream.write(i)
                }
              } catch {
                case e @ (_: IOException | _: InterruptedException) =>
                  readThreadAlive.set(false)
              }
            }
            if (socket.isEmpty && readThreadAlive.get) {
              try Thread.sleep(10)
              catch { case _: InterruptedException => }
            }
          }
        } catch { case e: IOException => e.printStackTrace(System.err) }
      }
    }
    @tailrec
    def blockUntilStart(): Unit = {
      if (socket.isEmpty) {
        socket = Try(ClientSocket.localSocket(bootSocketName, useJNI)).toOption
      }
      val stop = try {
        socket match {
          case None =>
            process.foreach { p =>
              val output = p.getInputStream
              while (output.available > 0) {
                printStream.write(output.read())
              }
            }
          case Some(s) =>
            while (!gotInputBack && !stdinBytes.isEmpty && socket.isDefined) {
              val out = s.getOutputStream
              val b = stdinBytes.poll
              // echo stdin during boot
              printStream.write(b)
              printStream.flush()
              out.write(b)
              out.flush()
            }
        }
        process.foreach { p =>
          val error = p.getErrorStream
          while (error.available > 0) {
            errorStream.write(error.read())
          }
        }
        false
      } catch { case e: IOException => true }
      Thread.sleep(10)
      printStream.flush()
      errorStream.flush()
      /*
       * If an earlier server process is launching, the process launched by this client
       * will return with exit value 2. In that case, we can treat the process as alive
       * even if it is actually dead.
       */
      val existsValidProcess = process.fold(socket.isDefined)(p => p.isAlive || p.exitValue == 2)
      if (!portfile.exists && !stop && readThreadAlive.get && existsValidProcess) {
        blockUntilStart()
      } else {
        socket.foreach { s =>
          s.getInputStream.close()
          s.getOutputStream.close()
          s.close()
        }
        readThread.interrupt()
        process.foreach { p =>
          p.getOutputStream.close()
          p.getErrorStream.close()
          p.getInputStream.close()
        }
      }
    }

    try blockUntilStart()
    catch { case t: Throwable => t.printStackTrace() } finally {
      sbtProcess.set(null)
      Util.ignoreResult(Runtime.getRuntime.removeShutdownHook(hook))
    }
    if (!portfile.exists()) throw new ServerFailedException
    if (attached.get && !stdinBytes.isEmpty) Option(inputThread.get).foreach(_.drain())
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
                      else if (i.field == "cachedTestNames")
                        resp.withCachedTestNames(
                          Converter.fromJson[Boolean](i.value).getOrElse(true)
                        )
                      else if (i.field == "cachedMainClassNames")
                        resp.withCachedMainClassNames(
                          Converter.fromJson[Boolean](i.value).getOrElse(true)
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
        case (`promptChannel`, _) =>
          batchMode.set(false)
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

  def connect(log: Boolean, prompt: Boolean): Boolean = {
    if (log) console.appendLog(Level.Info, "entering *experimental* thin client - BEEP WHIRR")
    try {
      init(prompt, retry = true)
      true
    } catch {
      case _: ServerFailedException =>
        console.appendLog(Level.Error, "failed to connect to server")
        false
    }
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
          if ((batchMode.get && pendingResults.isEmpty) || !cancelledTasks) exitAbruptly()
          else cancelled.set(false)
        } else exitAbruptly() // handles double ctrl+c to force a shutdown
      }
      withSignalHandler(handler, Signals.INT) {
        def block(): Int = {
          try this.synchronized(this.wait)
          catch { case _: InterruptedException => }
          if (exitClean.get) 0 else 1
        }
        if (interactive) block()
        else if (exit) 0
        else {
          batchMode.set(true)
          val res = batchExecute(userCommands.toList)
          if (!batchMode.get) block() else res
        }
      }
    }

  def batchExecute(userCommands: List[String]): Int = {
    val cmd = userCommands mkString " "
    printStream.println("> " + cmd)
    sendAndWait(cmd, None)
  }

  def getCompletions(query: String): Seq[String] = {
    val quoteCount = query.foldLeft(0) {
      case (count, '"') => count + 1
      case (count, _)   => count
    }
    val inQuote = quoteCount % 2 != 0
    val (rawPrefix, prefix, rawSuffix, suffix) = if (quoteCount > 0) {
      query.lastIndexOf('"') match {
        case -1 => (query, query, None, None) // shouldn't happen
        case i =>
          val rawPrefix = query.substring(0, i)
          val prefix = rawPrefix.replaceAllLiterally("\"", "").replaceAllLiterally("\\;", ";")
          val rawSuffix = query.substring(i).replaceAllLiterally("\\;", ";")
          val suffix = if (rawSuffix.length > 1) rawSuffix.substring(1) else ""
          (rawPrefix, prefix, Some(rawSuffix), Some(suffix))
      }
    } else (query, query.replaceAllLiterally("\\;", ";"), None, None)
    val tailSpace = query.endsWith(" ") || query.endsWith("\"")
    val sanitizedQuery = suffix.foldLeft(prefix) { _ + _ }
    def getCompletions(query: String, sendCommand: Boolean): Seq[String] = {
      val result = new LinkedBlockingQueue[CompletionResponse]()
      val json = s"""{"query":"$query","level":1}"""
      val execId = sendJson("sbt/completion", json)
      pendingCompletions.put(execId, result.put)
      val response = result.take
      def fillCompletions(label: String, regex: String, command: String): Seq[String] = {
        def updateCompletions(): Seq[String] = {
          errorStream.println()
          sendJson(attach, s"""{"interactive": false}""")
          sendAndWait(query.replaceAll(regex + ".*", command).trim, None)
          getCompletions(query, false)
        }
        if (noStdErr) Nil
        else if (noTab) updateCompletions()
        else {
          errorStream.print(s"\nNo cached $label names found. Press '<tab>' to compile: ")
          stdinBytes.take match {
            case 9 =>
              updateCompletions()
            case _ => Nil
          }
        }
      }
      val testNameCompletions =
        if (!response.cachedTestNames.getOrElse(true) && sendCommand)
          fillCompletions("test", "test(Only|Quick)", "definedTestNames")
        else Nil
      val classNameCompletions =
        if (!response.cachedMainClassNames.getOrElse(true) && sendCommand)
          fillCompletions("main class", "runMain", "discoveredMainClasses")
        else Nil
      val completions = response.items
      testNameCompletions ++ classNameCompletions ++ completions
    }
    getCompletions(sanitizedQuery, true) collect {
      case c if inQuote                      => c
      case c if tailSpace && c.contains(" ") => c.replaceAllLiterally(prefix, "")
      case c if !tailSpace                   => c.split(" ").last
    }
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
      connectionHolder.get match {
        case null =>
        case c    => c.shutdown()
      }
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
      val completionArguments: Seq[String],
      val sbtScript: String,
  ) {
    def withBaseDirectory(file: File): Arguments =
      new Arguments(file, sbtArguments, commandArguments, completionArguments, sbtScript)
  }
  private[client] val completions = "--completions"
  private[client] val noTab = "--no-tab"
  private[client] val noStdErr = "--no-stderr"
  private[client] val sbtBase = "--sbt-base-directory"
  private[client] def parseArgs(args: Array[String]): Arguments = {
    var sbtScript = if (Properties.isWin) "sbt.cmd" else "sbt"
    val commandArgs = new mutable.ArrayBuffer[String]
    val sbtArguments = new mutable.ArrayBuffer[String]
    val completionArguments = new mutable.ArrayBuffer[String]
    val SysProp = "-D([^=]+)=(.*)".r
    val sanitized = args.flatMap {
      case a if a.startsWith("\"") => Array(a)
      case a                       => a.split(" ")
    }
    var i = 0
    while (i < sanitized.length) {
      sanitized(i) match {
        case a if completionArguments.nonEmpty => completionArguments += a
        case a if commandArgs.nonEmpty         => commandArgs += a
        case a if a == noStdErr || a == noTab || a.startsWith(completions) =>
          completionArguments += a
        case a if a.startsWith("--sbt-script=") =>
          sbtScript = a.split("--sbt-script=").lastOption.getOrElse(sbtScript)
        case a if !a.startsWith("-") => commandArgs += a
        case a @ SysProp(key, value) =>
          System.setProperty(key, value)
          sbtArguments += a
        case a => sbtArguments += a
      }
      i += 1
    }
    val base = new File("").getCanonicalFile
    new Arguments(base, sbtArguments, commandArgs, completionArguments, sbtScript)
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
      if (client.connect(log = true, prompt = false)) client.run()
      else 1
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
      useJNI,
    )
  def main(args: Array[String]): Unit = {
    val (jnaArg, restOfArgs) = args.partition(_ == "--jna")
    val useJNI = jnaArg.isEmpty
    val base = new File("").getCanonicalFile
    if (restOfArgs.exists(_.startsWith(NetworkClient.completions)))
      System.exit(complete(base, restOfArgs, useJNI, System.in, System.out))
    else {
      val hook = new Thread(() => {
        System.out.print(ConsoleAppender.ClearScreenAfterCursor)
        System.out.flush()
      })
      Runtime.getRuntime.addShutdownHook(hook)
      System.exit(Terminal.withStreams {
        try client(base, restOfArgs, System.in, System.err, System.out, useJNI)
        finally {
          Runtime.getRuntime.removeShutdownHook(hook)
          hook.run()
        }
      })
    }
  }
  def complete(
      baseDirectory: File,
      args: Array[String],
      useJNI: Boolean,
      in: InputStream,
      out: PrintStream
  ): Int = {
    val cmd: String = args.find(_.startsWith(NetworkClient.completions)) match {
      case Some(c) =>
        c.split('=').lastOption match {
          case Some(query) =>
            query.indexOf(" ") match {
              case -1 => throw new IllegalArgumentException(query)
              case i  => query.substring(i + 1)
            }
          case _ => throw new IllegalArgumentException(c)
        }
      case _ => throw new IllegalStateException("should be unreachable")
    }
    val quiet = args.exists(_ == "--quiet")
    val errorStream = if (quiet) new PrintStream(_ => {}, false) else System.err
    val sbtArgs = args.takeWhile(!_.startsWith(NetworkClient.completions))
    val arguments = NetworkClient.parseArgs(sbtArgs)
    val noTab = args.contains("--no-tab")
    val client =
      simpleClient(
        arguments.withBaseDirectory(baseDirectory),
        inputStream = in,
        errorStream = errorStream,
        printStream = errorStream,
        useJNI = useJNI,
      )
    try {
      val results =
        if (client.connect(log = false, prompt = true)) client.getCompletions(cmd)
        else Nil
      out.println(results.sorted.distinct mkString "\n")
      0
    } catch { case _: Exception => 1 } finally client.close()
  }

  def run(configuration: xsbti.AppConfiguration, arguments: List[String]): Int =
    try {
      val client = new NetworkClient(configuration, parseArgs(arguments.toArray))
      try {
        if (client.connect(log = true, prompt = false)) client.run()
        else 1
      } catch { case _: Throwable => 1 } finally client.close()
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        1
    }
}
