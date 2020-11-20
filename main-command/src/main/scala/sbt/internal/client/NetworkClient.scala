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
import java.net.{ Socket, SocketException }
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import java.util.concurrent.{ ConcurrentHashMap, LinkedBlockingQueue, TimeUnit }

import sbt.BasicCommandStrings.{ Shutdown, TerminateAction }
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
  cancelReadSystemIn,
  cancelRequest,
  promptChannel,
  readSystemIn,
  systemIn,
  systemErr,
  systemOut,
  systemOutFlush,
  systemErrFlush,
  terminalCapabilities,
  terminalCapabilitiesResponse,
  terminalGetSize,
  terminalPropertiesQuery,
  terminalPropertiesResponse,
  terminalSetEcho,
  terminalSetRawMode,
  terminalSetSize,
  getTerminalAttributes,
  setTerminalAttributes,
}
import NetworkClient.Arguments
import java.util.concurrent.TimeoutException

trait ConsoleInterface {
  def appendLog(level: Level.Value, message: => String): Unit
  def success(msg: String): Unit
}

/**
 * A NetworkClient connects to a running an sbt instance or starts a
 * new instance if there isn't already one running. Once connected,
 * it can send commands for sbt to run, it can send completions to sbt
 * and print the completions to stdout so that a shell can consume
 * the completions or it can enter an interactive sbt shell session
 * in which it relays io bytes between sbt and the terminal.
 *
 * @param arguments   the arguments for the forked sbt server if the client
 *                    needs to start it. It also contains the sbt command
 *                    arguments to send to the server if any are present.
 * @param console     a logging instance. This can use a ConsoleAppender or
 *                    just simply print to a PrintSream.
 * @param inputStream the InputStream from which the client reads bytes. It
 *                    is not hardcoded to System.in so that a NetworkClient
 *                    can be remotely controlled by a java process, which
 *                    is useful in test.
 * @param errorStream the sink for messages that we always want to be printed.
 *                    It is usually System.err but could be overridden in tests
 *                    or set to a null OutputStream if the NetworkClient needs
 *                    to be silent
 * @param printStream the sink for standard out messages. It is typically
 *                    System.out but in the case of completions, the bytes written
 *                    to System.out are usually treated as completion results
 *                    so we need to reroute standard out messages to System.err.
 *                    It's also useful to override this in testing.
 * @param useJNI      toggles whether or not to use the jni based implementations
 *                    in org.scalasbt.ipcsocket. These are only available on
 *                    64 bit linux, mac and windows. Any other platform will need
 *                    to fall back on jna.
 */
class NetworkClient(
    arguments: Arguments,
    console: ConsoleInterface,
    inputStream: InputStream,
    errorStream: PrintStream,
    printStream: PrintStream,
    useJNI: Boolean,
) extends AutoCloseable { self =>
  def this(configuration: xsbti.AppConfiguration, arguments: Arguments) =
    this(
      arguments = arguments.withBaseDirectory(configuration.baseDirectory),
      console = NetworkClient.consoleAppenderInterface(System.out),
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
  private val pendingResults =
    new ConcurrentHashMap[String, (LinkedBlockingQueue[Integer], Long, String)]
  private val pendingCancellations = new ConcurrentHashMap[String, LinkedBlockingQueue[Boolean]]
  private val pendingCompletions = new ConcurrentHashMap[String, CompletionResponse => Unit]
  private val attached = new AtomicBoolean(false)
  private val attachUUID = new AtomicReference[String](null)
  private val connectionHolder = new AtomicReference[ServerConnection]
  private val batchMode = new AtomicBoolean(false)
  private val interactiveThread = new AtomicReference[Thread](null)
  private val rebooting = new AtomicBoolean(false)
  private lazy val noTab = arguments.completionArguments.contains("--no-tab")
  private lazy val noStdErr = arguments.completionArguments.contains("--no-stderr") &&
    System.getenv("SBTC_AUTO_COMPLETE") == null

  private def mkSocket(file: File): (Socket, Option[String]) = ClientSocket.socket(file, useJNI)

  private def portfile = arguments.baseDirectory / "project" / "target" / "active.json"

  def connection: ServerConnection = connectionHolder.synchronized {
    connectionHolder.get match {
      case null => init(promptCompleteUsers = false, retry = true)
      case c    => c
    }
  }

  private[this] val stdinBytes = new LinkedBlockingQueue[Integer]
  private[this] val inLock = new Object
  private[this] val inputThread = new AtomicReference[RawInputThread]
  private[this] val exitClean = new AtomicBoolean(true)
  private[this] val sbtProcess = new AtomicReference[Process](null)
  private class ConnectionRefusedException(t: Throwable) extends Throwable(t)
  private class ServerFailedException extends Exception
  private[this] def startInputThread(): Unit = inputThread.get match {
    case null => inputThread.set(new RawInputThread)
    case _    =>
  }

  private[sbt] def connectOrStartServerAndConnect(
      promptCompleteUsers: Boolean,
      retry: Boolean
  ): (Socket, Option[String]) =
    try {
      if (!portfile.exists) {
        if (promptCompleteUsers) {
          val msg = if (noTab) "" else "No sbt server is running. Press <tab> to start one..."
          errorStream.print(s"\n$msg")
          if (noStdErr) System.exit(0)
          else if (noTab) waitForServer(portfile, log = true, startServer = true)
          else {
            startInputThread()
            stdinBytes.poll(5, TimeUnit.SECONDS) match {
              case null => System.exit(0)
              case i if i == 9 =>
                errorStream.println("\nStarting server...")
                waitForServer(portfile, !promptCompleteUsers, startServer = true)
              case _ => System.exit(0)
            }
          }
        } else {
          waitForServer(portfile, log = true, startServer = true)
        }
      }
      @tailrec def connect(attempt: Int): (Socket, Option[String]) = {
        val res = try Some(mkSocket(portfile))
        catch {
          // This catches a pipe busy exception which can happen if two windows clients
          // attempt to connect in rapid succession
          case e: IOException if e.getMessage.contains("Couldn't open") && attempt < 10 =>
            if (e.getMessage.contains("Access is denied") || e.getMessage.contains("(5)")) {
              errorStream.println(s"Access denied for portfile $portfile")
              throw new NetworkClient.AccessDeniedException
            }
            None
          case e: IOException => throw new ConnectionRefusedException(e)
        }
        res match {
          case Some(r) => r
          case None    =>
            // Use a random sleep to spread out the competing processes
            Thread.sleep(new java.util.Random().nextInt(20).toLong)
            connect(attempt + 1)
        }
      }
      connect(0)
    } catch {
      case e: ConnectionRefusedException if retry =>
        if (Files.deleteIfExists(portfile.toPath))
          connectOrStartServerAndConnect(promptCompleteUsers, retry = false)
        else throw e
    }

  // Open server connection based on the portfile
  def init(promptCompleteUsers: Boolean, retry: Boolean): ServerConnection = {
    val (sk, tkn) = connectOrStartServerAndConnect(promptCompleteUsers, retry)
    val conn = new ServerConnection(sk) {
      override def onNotification(msg: JsonRpcNotificationMessage): Unit = {
        msg.method match {
          case `Shutdown` =>
            val (log, rebootCommands) = msg.params match {
              case Some(jvalue) =>
                Converter
                  .fromJson[(Boolean, Option[(String, String)])](jvalue)
                  .getOrElse((true, None))
              case _ => (false, None)
            }
            if (rebootCommands.nonEmpty) {
              rebooting.set(true)
              attached.set(false)
              connectionHolder.getAndSet(null) match {
                case null =>
                case c    => c.shutdown()
              }
              waitForServer(portfile, true, false)
              init(promptCompleteUsers = false, retry = false)
              attachUUID.set(sendJson(attach, s"""{"interactive": ${!batchMode.get}}"""))
              rebooting.set(false)
              rebootCommands match {
                case Some((execId, cmd)) if execId.nonEmpty =>
                  if (batchMode.get && !pendingResults.containsKey(execId) && cmd.nonEmpty) {
                    console.appendLog(
                      Level.Error,
                      s"received request to re-run unknown command '$cmd' after reboot"
                    )
                  } else if (cmd.nonEmpty) {
                    if (batchMode.get) sendCommand(ExecCommand(cmd, execId))
                    else
                      inLock.synchronized {
                        val toSend = cmd.getBytes :+ '\r'.toByte
                        toSend.foreach(b => sendNotification(systemIn, b.toString))
                      }
                  } else completeExec(execId, 0)
                case _ =>
              }
            } else {
              if (!rebooting.get() && running.compareAndSet(true, false) && log) {
                if (!arguments.commandArguments.contains(Shutdown)) {
                  console.appendLog(Level.Error, "sbt server disconnected")
                  exitClean.set(false)
                }
              } else {
                console.appendLog(Level.Info, s"${if (log) "sbt server " else ""}disconnected")
              }
              stdinBytes.offer(-1)
              Option(inputThread.get).foreach(_.close())
              Option(interactiveThread.get).foreach(_.interrupt)
            }
          case `readSystemIn` => startInputThread()
          case `cancelReadSystemIn` =>
            inputThread.get match {
              case null =>
              case t    => t.close()
            }
          case _ => self.onNotification(msg)
        }
      }
      override def onRequest(msg: JsonRpcRequestMessage): Unit = self.onRequest(msg)
      override def onResponse(msg: JsonRpcResponseMessage): Unit = self.onResponse(msg)
      override def onShutdown(): Unit = if (!rebooting.get) {
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
  }

  /**
   * Forks another instance of sbt in the background.
   * This instance must be shutdown explicitly via `sbt -client shutdown`
   */
  def waitForServer(portfile: File, log: Boolean, startServer: Boolean): Unit = {
    val bootSocketName =
      BootServerSocket.socketLocation(arguments.baseDirectory.toPath.toRealPath())

    /*
     * For unknown reasons, linux sometimes struggles to connect to the socket in some
     * scenarios.
     */
    var socket: Option[Socket] =
      if (!Properties.isLinux) Try(ClientSocket.localSocket(bootSocketName, useJNI)).toOption
      else None
    val term = Terminal.console
    term.exitRawMode()
    val process = socket match {
      case None if startServer =>
        if (log) console.appendLog(Level.Info, "server was not detected. starting an instance")

        val props =
          Seq(
            term.getWidth,
            term.getHeight,
            term.isAnsiSupported,
            term.isColorEnabled,
            term.isSupershellEnabled
          ).mkString(",")

        val cmd = List(arguments.sbtScript) ++ arguments.sbtArguments ++
          List(BasicCommandStrings.DashDashDetachStdio, BasicCommandStrings.DashDashServer)
        val processBuilder =
          new ProcessBuilder(cmd: _*)
            .directory(arguments.baseDirectory)
            .redirectInput(Redirect.PIPE)
        processBuilder.environment.put(Terminal.TERMINAL_PROPS, props)
        val process = processBuilder.start()
        sbtProcess.set(process)
        Some(process)
      case _ =>
        if (log) {
          console.appendLog(Level.Info, "sbt server is booting up")
        }
        None
    }
    if (!startServer) {
      val deadline = 5.seconds.fromNow
      while (socket.isEmpty && !deadline.isOverdue) {
        socket = Try(ClientSocket.localSocket(bootSocketName, useJNI)).toOption
        if (socket.isEmpty) Thread.sleep(20)
      }
    }
    val hook = new Thread(() => Option(sbtProcess.get).foreach(_.destroyForcibly()))
    Runtime.getRuntime.addShutdownHook(hook)
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
            if (socket.isEmpty) {
              socket = Try(ClientSocket.localSocket(bootSocketName, useJNI)).toOption
            }
            socket.foreach { s =>
              try {
                s.getInputStream.read match {
                  case -1 | 0            => readThreadAlive.set(false)
                  case 2                 => gotInputBack = true
                  case 5                 => term.enterRawMode(); startInputThread()
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
      val existsValidProcess =
        process.fold(readThreadAlive.get)(p => p.isAlive || (Properties.isWin || p.exitValue == 2))
      if (!portfile.exists && !stop && existsValidProcess) {
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
  private def completeExec(execId: String, exitCode: => Int): Unit =
    pendingResults.remove(execId) match {
      case null =>
      case (q, startTime, name) =>
        val now = System.currentTimeMillis
        val message = timing(startTime, now)
        val ec = exitCode
        if (batchMode.get || !attached.get) {
          if (ec == 0) console.success(message)
          else console.appendLog(Level.Error, message)
        }
        Util.ignoreResult(q.offer(ec))
    }
  def onResponse(msg: JsonRpcResponseMessage): Unit = {
    completeExec(msg.id, getExitCode(msg.result))
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
          Converter.fromJson[Array[Byte]](json) match {
            case Success(bytes) if bytes.nonEmpty && attached.get =>
              synchronized(printStream.write(bytes))
            case _ =>
          }
          Vector.empty
        case (`systemErr`, Some(json)) =>
          Converter.fromJson[Array[Byte]](json) match {
            case Success(bytes) if bytes.nonEmpty && attached.get =>
              synchronized(errorStream.write(bytes))
            case _ =>
          }
          Vector.empty
        case (`systemOutFlush`, _) =>
          synchronized(printStream.flush())
          Vector.empty
        case (`systemErrFlush`, _) =>
          synchronized(errorStream.flush())
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
        case (`Shutdown`, Some(_))                => Vector.empty
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
    import sbt.protocol.codec.JsonProtocol._
    (msg.method, msg.params) match {
      case (`terminalCapabilities`, Some(json)) =>
        Converter.fromJson[TerminalCapabilitiesQuery](json) match {
          case Success(terminalCapabilitiesQuery) =>
            val response = TerminalCapabilitiesResponse(
              terminalCapabilitiesQuery.boolean
                .map(Terminal.console.getBooleanCapability(_)),
              terminalCapabilitiesQuery.numeric
                .map(c => Option(Terminal.console.getNumericCapability(c)).fold(-1)(_.toInt)),
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
      case (`setTerminalAttributes`, Some(json)) =>
        Converter.fromJson[TerminalSetAttributesCommand](json) match {
          case Success(attributes) =>
            val attrs = Map(
              "iflag" -> attributes.iflag,
              "oflag" -> attributes.oflag,
              "cflag" -> attributes.cflag,
              "lflag" -> attributes.lflag,
              "cchars" -> attributes.cchars,
            )
            Terminal.console.setAttributes(attrs)
            sendCommandResponse("", TerminalSetAttributesResponse(), msg.id)
          case Failure(_) =>
        }
      case (`getTerminalAttributes`, _) =>
        val attrs = Terminal.console.getAttributes
        val response = TerminalAttributesResponse(
          iflag = attrs.getOrElse("iflag", ""),
          oflag = attrs.getOrElse("oflag", ""),
          cflag = attrs.getOrElse("cflag", ""),
          lflag = attrs.getOrElse("lflag", ""),
          cchars = attrs.getOrElse("cchars", ""),
        )
        sendCommandResponse("", response, msg.id)
      case (`terminalGetSize`, _) =>
        val response = TerminalGetSizeResponse(
          Terminal.console.getWidth,
          Terminal.console.getHeight,
        )
        sendCommandResponse("", response, msg.id)
      case (`terminalSetSize`, Some(json)) =>
        Converter.fromJson[TerminalSetSizeCommand](json) match {
          case Success(size) =>
            Terminal.console.setSize(size.width, size.height)
            sendCommandResponse("", TerminalSetSizeResponse(), msg.id)
          case Failure(_) =>
        }
      case (`terminalSetEcho`, Some(json)) =>
        Converter.fromJson[TerminalSetEchoCommand](json) match {
          case Success(echo) =>
            Terminal.console.setEchoEnabled(echo.toggle)
            sendCommandResponse("", TerminalSetEchoResponse(), msg.id)
          case Failure(_) =>
        }
      case (`terminalSetRawMode`, Some(json)) =>
        Converter.fromJson[TerminalSetRawModeCommand](json) match {
          case Success(raw) =>
            if (raw.toggle) Terminal.console.enterRawMode()
            else Terminal.console.exitRawMode()
            sendCommandResponse("", TerminalSetRawModeResponse(), msg.id)
          case Failure(_) =>
        }
      case _ =>
    }
  }

  def connect(log: Boolean, promptCompleteUsers: Boolean): Boolean = {
    if (log) console.appendLog(Level.Info, "entering *experimental* thin client - BEEP WHIRR")
    try {
      init(promptCompleteUsers, retry = true)
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
      val userCommands = cleaned.takeWhile(_ != TerminateAction)
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
        console.appendLog(Level.Info, "terminate the server with `shutdown`")
        if (interactive) {
          console.appendLog(Level.Info, "disconnect from the server with `exit`")
          block()
        } else if (exit) 0
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
      val response = result.poll(30, TimeUnit.SECONDS) match {
        case null => throw new TimeoutException("no response from server within 30 seconds")
        case r    => r
      }
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
          startInputThread()
          stdinBytes.poll(5, TimeUnit.SECONDS) match {
            case null        => Nil
            case i if i == 9 => updateCompletions()
            case _           => Nil
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
        case _: InterruptedException if cmd == Shutdown => result = 0
        case _: InterruptedException                    => result = if (exitClean.get) 0 else 1
      }
    }
    if (result == null) 1 else result
  }

  def sendExecCommand(commandLine: String): LinkedBlockingQueue[Integer] = {
    val execId = UUID.randomUUID.toString
    val queue = new LinkedBlockingQueue[Integer]
    sendCommand(ExecCommand(commandLine, execId))
    pendingResults.put(execId, (queue, System.currentTimeMillis, commandLine))
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
      case e: SocketException if command.toString.contains("exit") => running.set(false)
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
    sendJson(method, params, uuid)
    uuid
  }
  def sendJson(method: String, params: String, uuid: String): Unit = {
    val msg = s"""{ "jsonrpc": "2.0", "id": "$uuid", "method": "$method", "params": $params }"""
    connection.sendString(msg)
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
        case c =>
          try sendExecCommand("exit")
          finally c.shutdown()
      }
      Option(inputThread.get).foreach(_.interrupt())
    } catch {
      case t: Throwable => t.printStackTrace(); throw t
    }

  private[this] class RawInputThread extends Thread("sbt-read-input-thread") with AutoCloseable {
    setDaemon(true)
    start()
    val stopped = new AtomicBoolean(false)
    override final def run(): Unit = {
      def read(): Unit = {
        val b = inputStream.read
        inLock.synchronized(stdinBytes.offer(b))
        if (attached.get()) drain()
      }
      try read()
      catch { case _: InterruptedException | NonFatal(_) => stopped.set(true) } finally {
        inputThread.set(null)
      }
    }

    def drain(): Unit = inLock.synchronized {
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
  private def simpleConsoleInterface(doPrintln: String => Unit): ConsoleInterface =
    new ConsoleInterface {
      import scala.Console.{ GREEN, RED, RESET, YELLOW }
      override def appendLog(level: Level.Value, message: => String): Unit = synchronized {
        val prefix = level match {
          case Level.Error => s"[$RED$level$RESET]"
          case Level.Warn  => s"[$YELLOW$level$RESET]"
          case _           => s"[$RESET$level$RESET]"
        }
        message.linesIterator.foreach(line => doPrintln(s"$prefix $line"))
      }
      override def success(msg: String): Unit = doPrintln(s"[${GREEN}success$RESET] $msg")
    }
  private[client] class Arguments(
      val baseDirectory: File,
      val sbtArguments: Seq[String],
      val commandArguments: Seq[String],
      val completionArguments: Seq[String],
      val sbtScript: String,
      val bsp: Boolean,
  ) {
    def withBaseDirectory(file: File): Arguments =
      new Arguments(file, sbtArguments, commandArguments, completionArguments, sbtScript, bsp)
  }
  private[client] val completions = "--completions"
  private[client] val noTab = "--no-tab"
  private[client] val noStdErr = "--no-stderr"
  private[client] val sbtBase = "--sbt-base-directory"
  private[client] def parseArgs(args: Array[String]): Arguments = {
    var sbtScript = if (Properties.isWin) "sbt.bat" else "sbt"
    var bsp = false
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
          sbtScript = a
            .split("--sbt-script=")
            .lastOption
            .map(_.replaceAllLiterally("%20", " "))
            .getOrElse(sbtScript)
        case "-bsp" | "--bsp" => bsp = true
        case "--sbt-script" if i + 1 < sanitized.length =>
          i += 1
          sbtScript = sanitized(i).replaceAllLiterally("%20", " ")
        case a if !a.startsWith("-") => commandArgs += a
        case a @ SysProp(key, value) =>
          System.setProperty(key, value)
          sbtArguments += a
        case a => sbtArguments += a
      }
      i += 1
    }
    val base = new File("").getCanonicalFile
    if (!sbtArguments.contains("-Dsbt.io.virtual=true")) sbtArguments += "-Dsbt.io.virtual=true"
    new Arguments(base, sbtArguments, commandArgs, completionArguments, sbtScript, bsp)
  }

  def client(
      baseDirectory: File,
      args: Array[String],
      inputStream: InputStream,
      printStream: PrintStream,
      errorStream: PrintStream,
      useJNI: Boolean
  ): Int = {
    val client =
      simpleClient(
        NetworkClient.parseArgs(args).withBaseDirectory(baseDirectory),
        inputStream,
        printStream,
        errorStream,
        useJNI,
      )
    try {
      if (client.connect(log = true, promptCompleteUsers = false)) client.run()
      else 1
    } catch { case _: Exception => 1 } finally client.close()
  }
  def client(
      baseDirectory: File,
      args: Arguments,
      inputStream: InputStream,
      errorStream: PrintStream,
      terminal: Terminal,
      useJNI: Boolean
  ): Int = {
    val printStream = if (args.bsp) errorStream else terminal.printStream
    val client =
      simpleClient(
        args.withBaseDirectory(baseDirectory),
        inputStream,
        printStream,
        errorStream,
        useJNI,
      )
    clientImpl(client, args.bsp)
  }
  private def clientImpl(client: NetworkClient, isBsp: Boolean): Int = {
    try {
      if (isBsp) {
        val (socket, _) =
          client.connectOrStartServerAndConnect(promptCompleteUsers = false, retry = true)
        BspClient.bspRun(socket)
      } else {
        if (client.connect(log = true, promptCompleteUsers = false)) client.run()
        else 1
      }
    } catch { case _: Exception => 1 } finally client.close()
  }
  def client(
      baseDirectory: File,
      args: Array[String],
      inputStream: InputStream,
      errorStream: PrintStream,
      terminal: Terminal,
      useJNI: Boolean
  ): Int = client(baseDirectory, parseArgs(args), inputStream, errorStream, terminal, useJNI)

  private def simpleClient(
      arguments: Arguments,
      inputStream: InputStream,
      errorStream: PrintStream,
      useJNI: Boolean,
      terminal: Terminal
  ): NetworkClient = {
    val doPrint: String => Unit = line => {
      if (terminal.getLastLine.isDefined) terminal.printStream.println()
      terminal.printStream.println(line)
    }
    val interface = NetworkClient.simpleConsoleInterface(doPrint)
    val printStream = terminal.printStream
    new NetworkClient(arguments, interface, inputStream, errorStream, printStream, useJNI)
  }
  private def simpleClient(
      arguments: Arguments,
      inputStream: InputStream,
      printStream: PrintStream,
      errorStream: PrintStream,
      useJNI: Boolean,
  ): NetworkClient = {
    val interface = NetworkClient.simpleConsoleInterface(printStream.println)
    new NetworkClient(arguments, interface, inputStream, errorStream, printStream, useJNI)
  }
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
      if (Util.isNonCygwinWindows) sbt.internal.util.JLine3.forceWindowsJansi()
      val parsed = parseArgs(restOfArgs)
      System.exit(Terminal.withStreams(isServer = false, isSubProcess = false) {
        val term = Terminal.console
        try client(base, parsed, term.inputStream, System.err, term, useJNI)
        catch { case _: AccessDeniedException => 1 } finally {
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
    try {
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
          if (client.connect(log = false, promptCompleteUsers = true)) client.getCompletions(cmd)
          else Nil
        out.println(results.sorted.distinct mkString "\n")
        0
      } catch { case _: Exception => 1 } finally client.close()
    } catch { case _: AccessDeniedException => 1 }
  }

  def run(configuration: xsbti.AppConfiguration, arguments: List[String]): Int =
    run(configuration, arguments, false)
  def run(
      configuration: xsbti.AppConfiguration,
      arguments: List[String],
      redirectOutput: Boolean
  ): Int = {
    val term = Terminal.console
    val err = new PrintStream(term.errorStream)
    val out = if (redirectOutput) err else new PrintStream(term.outputStream)
    val args = parseArgs(arguments.toArray).withBaseDirectory(configuration.baseDirectory)
    val client = simpleClient(args, term.inputStream, out, err, useJNI = false)
    clientImpl(client, args.bsp)
  }
  private class AccessDeniedException extends Throwable
}
