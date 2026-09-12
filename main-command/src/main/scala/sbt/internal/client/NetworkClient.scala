/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package client

import java.io.{ File, IOException, InputStream, PrintStream }
import java.lang.ProcessBuilder.Redirect
import java.net.{ Socket, SocketException }
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.{ MessageDigest, SecureRandom }
import java.util.{ Base64, UUID }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import java.util.concurrent.{
  ConcurrentHashMap,
  CountDownLatch,
  LinkedBlockingQueue,
  Semaphore,
  TimeUnit,
}

import sbt.BasicCommandStrings.{ DashDashDetachStdio, DashDashServer, Shutdown, TerminateAction }
import sbt.internal.langserver.{ LogMessageParams, MessageType, PublishDiagnosticsParams }
import sbt.internal.worker.{ ClientJobParams, NativeRunInfo, RunInfo }
import sbt.internal.protocol.*
import sbt.internal.util.{
  ConsoleAppender,
  ConsoleOut,
  MessageOnlyException,
  RunHandler,
  Signals,
  Terminal,
  Util
}
import sbt.io.{ Hash, IO }
import sbt.io.syntax.*
import sbt.protocol.*
import sbt.util.{ HashUtil, Level, Logger }
import sjsonnew.BasicJsonProtocol.*
import sjsonnew.shaded.scalajson.ast.unsafe.{ JObject, JValue }
import sjsonnew.support.scalajson.unsafe.Converter

import scala.annotation.tailrec
import scala.collection.immutable.TreeMap
import scala.collection.mutable
import scala.concurrent.duration.*
import scala.util.control.NonFatal
import scala.util.{ Failure, Properties, Success, Try }
import Serialization.{
  CancelAll,
  attach,
  cancelReadSystemIn,
  cancelRequest,
  clientJob,
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

trait ConsoleInterface:
  def appendLog(level: Level.Value, message: => String): Unit
  def success(msg: String): Unit

/**
 * A NetworkClient connects to a running sbt instance or starts a
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
 *                    just simply print to a PrintStream.
 * @param inputStream the InputStream from which the client reads bytes. It
 *                    is not hardcoded to System.in so that a NetworkClient
 *                    can be remotely controlled by a java process, which
 *                    is useful in testing.
 * @param errorStream the sink for messages that we always want to be printed.
 *                    It is usually System.err but could be overridden in tests
 *                    or set to a null OutputStream if the NetworkClient needs
 *                    to be silent.
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
) extends AutoCloseable:
  self =>
  def this(configuration: xsbti.AppConfiguration, arguments: Arguments) =
    this(
      arguments = arguments.withoutSysProps.withBaseDirectory(configuration.baseDirectory),
      console = NetworkClient.consoleAppenderInterface(System.out),
      inputStream = System.in,
      errorStream = System.err,
      printStream = System.out,
      useJNI = false,
    )
  def this(configuration: xsbti.AppConfiguration, args: List[String]) =
    this(
      console = NetworkClient.consoleAppenderInterface(System.out),
      arguments = NetworkClient
        .parseArgs(args.toArray)
        .withoutSysProps
        .withBaseDirectory(configuration.baseDirectory),
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
  private val pendingResponseHandlers =
    new ConcurrentHashMap[String, JsonRpcResponseMessage => Unit]
  private val attached = new AtomicBoolean(false)
  private val attachUUID = new AtomicReference[String](null)
  private val connectionHolder = AtomicCloseable[ServerSession]()
  private val batchMode = new AtomicBoolean(false)
  private val interactiveThread = new AtomicReference[Thread](null)
  private val rebooting = new AtomicBoolean(false)
  private lazy val noTab = arguments.completionArguments.contains("--no-tab")
  private lazy val noStdErr = arguments.completionArguments.contains("--no-stderr") &&
    !sys.env.contains("SBTN_AUTO_COMPLETE") && !sys.env.contains("SBTC_AUTO_COMPLETE")
  private def shutdownOnly = arguments.commandArguments == Seq(Shutdown)
  private def exitOnly = arguments.commandArguments == Seq(TerminateAction)
  private lazy val serverAutoStart: Boolean =
    sys.props.get("sbt.server.autostart").forall(_.toLowerCase == "true")
  private lazy val serverAutoRestart: Boolean =
    sys.props.get("sbt.server.autorestart").forall(_.toLowerCase == "true")

  private def mkSocket(file: File): (Socket, Option[String]) = ClientSocket.socket(file, useJNI)

  private[sbt] def logFailure(e: Exception): Unit =
    errorStream.println(s"sbt client failed: $e")
    e.printStackTrace(errorStream)

  private def portfile = arguments.baseDirectory / "project" / "target" / "active.json"

  // initImpl may start a server, which no later discard can undo, so one caller at a time
  def connection: ServerSession = connectionHolder.ref.synchronized {
    connectionHolder.setIfEmpty(initImpl(promptCompleteUsers = false, retry = true))
  }

  private val stdinBytes = new LinkedBlockingQueue[Integer]
  private val inLock = new Object
  // A single persistent reader for the life of the client.
  private val inputThread = new RawInputThread
  private val exitClean = new AtomicBoolean(true)
  private val inClientSideRun = new AtomicBoolean(false)
  private val sbtProcess = new AtomicReference[Process](null)
  private class ConnectionRefusedException(t: Throwable) extends Throwable(t)
  private class ServerFailedException extends Exception
  private[client] def startInputThread(): Unit = inputThread.request()
  private lazy val log: Logger = new Logger:
    def trace(t: => Throwable): Unit = ()
    def success(message: => String): Unit = ()
    def log(level: Level.Value, message: => String): Unit = console.appendLog(level, message)
  private val interactive = arguments.commandArguments.isEmpty
  private val startupMessages: List[String] =
    "entering thin client - BEEP WHIRR" ::
      "starting sbt server in the background" ::
      "use 'sbt shutdown' to shutdown the server" ::
      " " :: Nil

  /**
   * A running server was started with its own `-D` options, and the ones passed to this
   * invocation would be dropped on the floor. Restart the server so that they take effect,
   * or, when this client isn't the one to do that, say which options it is missing rather
   * than let them go by unmentioned. A server a client started with no options at all is
   * one that runs without any, so a client that carries some restarts it too, but a server
   * no client started keeps whatever it has. Completion queries never restart it: a client
   * is not worth a server to someone pressing tab.
   */
  private def restartServerIfSysPropsChanged(promptCompleteUsers: Boolean): Unit =
    val checked = arguments.forwardsSysProps && !shutdownOnly && !exitOnly && !arguments.bsp &&
      !promptCompleteUsers
    if checked then
      // a -D option written after the command is parsed as part of the command, so it isn't
      // ours to compare and the server isn't missing it either. Only the name it defines is
      // left out: everything after the first command lands in commandArguments, so letting
      // one of these turn the whole comparison off would drop the options written before it,
      // which is the very thing #9682 is about.
      val deferred = arguments.commandArguments
        .filter(_.startsWith("-D"))
        .map(NetworkClient.sysPropName)
        .toSet
      val current = NetworkClient.serverSysProps(arguments.sbtArguments)
      ClientSocket.loadPortFile(portfile).foreach { pf =>
        val (dropped, added, changed) =
          NetworkClient.sysPropsDiff(pf.sysProps, current, deferred)
        if (dropped ++ added ++ changed).nonEmpty then
          // a server nothing recorded the options of may well have the ones this client
          // carries already, and an editor's server is the usual one to be in that state,
          // so it gets a word rather than a shutdown it never asked for
          val known = pf.sysPropsRecorded.contains(true)
          val restarts = known && serverAutoStart && serverAutoRestart
          val level = if restarts then Level.Info else Level.Warn
          // the values are what a credential would be hiding in, so only the names of
          // the options are worth saying out loud
          console.appendLog(
            level,
            if restarts then "sbt server is running with different JVM options; restarting it"
            else if known then
              "sbt server is running with different JVM options, which it cannot pick up"
            else
              "sbt server was started by something other than the thin client, so it may "
                + "not have these JVM options"
          )
          if dropped.nonEmpty then console.appendLog(level, s"dropped: ${dropped.mkString(" ")}")
          if added.nonEmpty then console.appendLog(level, s"added: ${added.mkString(" ")}")
          if changed.nonEmpty then console.appendLog(level, s"changed: ${changed.mkString(" ")}")
          if !restarts then console.appendLog(level, "run 'sbt shutdown' for them to take effect")
          else
            shutdownRunningServer(pf.uri) match
              case Some(true)  => ()
              case Some(false) =>
                console.appendLog(
                  Level.Error,
                  "the sbt server did not shut down, it is most likely busy with another client"
                )
                // the request is queued on it and stays there, so a second one buys nothing
                console.appendLog(
                  Level.Error,
                  "it has the request and takes it once that work is done, which ends that"
                    + " client's session too"
                )
                console.appendLog(Level.Error, "run this command again once the server is gone")
                throw new ServerFailedException
              case None =>
                // it answers no socket but is still there, which says nothing about how
                // busy it is, so leaving it alone beats failing an invocation over it
                console.appendLog(
                  Level.Warn,
                  "the sbt server could not be reached to restart it; it keeps the JVM"
                    + " options it was started with"
                )
                console.appendLog(
                  Level.Warn,
                  "run 'sbt shutdown' for the ones passed here to take effect"
                )
          end if
        end if
      }
    end if
  end restartServerIfSysPropsChanged

  /**
   * Asks the running server to shut down and waits for it to let go of its socket, so that
   * the server started next doesn't run into the one it replaces. `Some(false)` means it was
   * asked and is still there, `None` that it couldn't be asked at all.
   */
  private def shutdownRunningServer(uri: String): Option[Boolean] =
    def gone = !portfile.exists && !ClientSocket.reachable(uri, useJNI)
    @tailrec def socketOpt(attempt: Int): Option[(Socket, Option[String])] =
      Try(mkSocket(portfile)).toOption match
        case Some(sk)             => Some(sk)
        case None if attempt < 10 =>
          // the socket can be momentarily busy, and the connection file can be mid-write,
          // both of which the connect path retries as well
          Thread.sleep(new java.util.Random().nextInt(20).toLong)
          socketOpt(attempt + 1)
        case None => None
    socketOpt(0) match
      // a server that stopped answering leaves a stale portfile, which the caller replaces
      case None            => if ClientSocket.reachable(uri, useJNI) then None else Some(true)
      case Some((sk, tkn)) =>
        val session = new ServerSessionImpl(sk, "sbt-server-restart")
        try
          val opts = InitializeOption(
            token = tkn,
            skipAnalysis = Some(true),
            canWork = Some(false),
            subscribeToAll = Some(false),
          )
          val asked =
            for
              _ <- session.sendCommand(
                InitCommand(
                  token = tkn,
                  execId = Option(UUID.randomUUID.toString),
                  skipAnalysis = Some(true),
                  initializationOptions = Some(opts),
                )
              )
              _ <- session.sendCommand(ExecCommand(Shutdown, Option(UUID.randomUUID.toString)))
            yield ()
          // a send that fails either lost the connection to a server that is already on its
          // way out, which settles in a moment, or never reached one that is still up and
          // never will, and waiting the full timeout on that second case says nothing that
          // the first second didn't
          val waitFor =
            if asked.isFailure then NetworkClient.serverShutdownGrace
            else NetworkClient.serverShutdownTimeout
          val deadline = waitFor.fromNow
          // the server drops the portfile when it starts tearing down, and only then is it
          // worth asking its socket whether it is still there
          while portfile.exists && !deadline.isOverdue() do Thread.sleep(20)
          // each of these asks costs a connection on a server that is still up, so they
          // get further apart the longer it takes
          var delay = 20L
          while !gone && !deadline.isOverdue() do
            Thread.sleep(delay)
            if delay < 500 then delay = delay * 2
          Some(gone)
        finally session.close()
        end try
    end match
  end shutdownRunningServer

  private[sbt] def connectOrStartServerAndConnect(
      promptCompleteUsers: Boolean,
      retry: Boolean
  ): (Socket, Option[String]) =
    try
      if portfile.exists then restartServerIfSysPropsChanged(promptCompleteUsers)
      if !portfile.exists then
        if shutdownOnly then
          console.appendLog(Level.Info, "no sbt server is running. ciao")
          System.exit(0)
        else if !serverAutoStart then
          console.appendLog(Level.Error, "no sbt server is running (sbt.server.autostart=false)")
          System.exit(1)
        else if promptCompleteUsers then
          val msg = if noTab then "" else "No sbt server is running. Press <tab> to start one..."
          errorStream.print(s"\n$msg")
          if noStdErr then System.exit(0)
          else if noTab then waitForServer(portfile, log = true, startServer = true)
          else
            startInputThread()
            stdinBytes.poll(5, TimeUnit.SECONDS) match
              case null        => System.exit(0)
              case i if i == 9 =>
                errorStream.println("\nStarting server...")
                waitForServer(portfile, !promptCompleteUsers, startServer = true)
              case _ => System.exit(0)
        else waitForServer(portfile, log = true, startServer = true)
      end if
      @tailrec def connect(attempt: Int): (Socket, Option[String]) =
        val res =
          try Some(mkSocket(portfile))
          catch
            case _: ClientSocket.ConnectionFileReadException if attempt < 10 =>
              None // server may be in the middle of writing the portfile
            case e: IOException =>
              if attempt >= 10 then throw new ConnectionRefusedException(e)
              val msg = Option(e.getMessage).getOrElse("")
              // This catches a pipe busy exception which can happen if two windows clients
              // attempt to connect in rapid succession
              if msg.contains("Couldn't open") then
                if msg.contains("Access is denied") || msg.contains("(5)") then
                  errorStream.println(s"Access denied for portfile $portfile")
                  throw new NetworkClient.AccessDeniedException
              None // server could be busy, not down, so try again
        res match
          case Some(r) => r
          case None    =>
            // Use a random sleep to spread out the competing processes
            Thread.sleep(new java.util.Random().nextInt(20).toLong)
            connect(attempt + 1)
      end connect
      connect(0)
    catch
      case e @ (_: ConnectionRefusedException | _: ClientSocket.ConnectionFileReadException)
          if retry =>
        errorStream.println(s"${e.getMessage}; starting a new server")
        if Files.deleteIfExists(portfile.toPath) then
          connectOrStartServerAndConnect(promptCompleteUsers, retry = false)
        else throw e
  end connectOrStartServerAndConnect

  // Open server connection based on the portfile
  private def initImpl(promptCompleteUsers: Boolean, retry: Boolean): ServerSession =
    val (sk, tkn) = connectOrStartServerAndConnect(promptCompleteUsers, retry)
    val conn = new ServerSessionImpl(sk, s"sbt-serverconnection-${sk.getPort}"):
      override protected def onNotification(msg: JsonRpcNotificationMessage): Unit =
        msg.method match
          case `Shutdown` =>
            val (log, rebootCommands) = msg.params match
              case Some(jvalue) =>
                Converter
                  .fromJson[(Boolean, Option[(String, String)])](jvalue)
                  .getOrElse((true, None))
              case _ => (false, None)
            if rebootCommands.nonEmpty then
              rebooting.set(true)
              attached.set(false)
              connectionHolder.close()
              waitForServer(portfile, true, false)
              init(promptCompleteUsers = false, retry = false)
              attachUUID.set(sendJson(attach, s"""{"interactive": ${!batchMode.get}}"""))
              rebooting.set(false)
              rebootCommands match
                case Some((execId, cmd)) if execId.nonEmpty =>
                  if cmd.isEmpty then completeExec(execId, 0)
                  else if !batchMode.get then
                    inLock.synchronized {
                      val toSend = cmd.getBytes :+ '\r'.toByte
                      toSend.foreach(b => sendNotification(systemIn, b.toString))
                    }
                  else if pendingResults.containsKey(execId) then
                    self.sendCommand(ExecCommand(cmd, execId))
                  else
                    console.appendLog(
                      Level.Error,
                      s"received request to re-run unknown command '$cmd' after reboot"
                    )
                case _ =>
              end match
            else
              if !rebooting.get() && running.compareAndSet(true, false) && log then
                if !arguments.commandArguments.contains(Shutdown) then
                  console.appendLog(Level.Error, "sbt server disconnected")
                  exitClean.set(false)
              else
                console.appendLog(Level.Info, s"${if log then "sbt server " else ""}disconnected")
              stdinBytes.offer(-1)
              inputThread.close()
              Option(interactiveThread.get).foreach(_.interrupt)
            end if
          case `readSystemIn`       => startInputThread()
          case `cancelReadSystemIn` => inputThread.cancel()
          case _                    => self.onNotification(msg)
      override protected def onRequest(msg: JsonRpcRequestMessage): Unit = self.onRequest(msg)
      override protected def onResponse(msg: JsonRpcResponseMessage): Unit = self.onResponse(msg)
      override protected def onClose(): Unit = if !rebooting.get then
        if exitClean.get then
          val serverDropped = running.get
          exitClean.set(!serverDropped)
          if serverDropped && !shutdownOnly then
            console.appendLog(Level.Error, "sbt server disconnected")
        running.set(false)
        Option(interactiveThread.get).foreach(_.interrupt())
    // initiate handshake
    val settled = CountDownLatch(1)
    initiateHandshake(Handshake(conn, settled), tkn)
    // the server refuses every other request until the handshake settles, retries included
    if !settled.await(connectTimeout.toMillis, TimeUnit.MILLISECONDS) then
      console.appendLog(Level.Error, "sbt server did not answer the handshake")
    conn
  end initImpl

  private final class Handshake(session: ServerSession, settled: CountDownLatch):
    private val attempt = new AtomicInteger(1)
    def release(): Unit = settled.countDown()
    def release(msg: String): Unit =
      release()
      console.appendLog(Level.Error, msg)
    def nextAttempt: Boolean = attempt.getAndIncrement < NetworkClient.handshakeAttemptLimit
    def initiateFailed(command: CommandMessage): Boolean =
      val failed = session.sendCommand(command).isFailure
      if failed then release()
      failed

  private def initiateHandshake(handshake: Handshake, token: Option[String]): Unit =
    val execId = UUID.randomUUID.toString
    // one entry per handshake in flight, so two connections cannot overwrite each other
    def handleHandshakeResponse(msg: JsonRpcResponseMessage): Unit =
      msg.error match
        case Some(err) => // Another client could have spent the token, so read it again
          if handshake.nextAttempt then
            Try(ClientSocket.token(portfile)).fold(
              e => handshake.release(s"sbt client could not read the token: $e"),
              token => initiateHandshake(handshake, token)
            )
          else handshake.release(s"sbt server refused the connection: ${err.message}")
        case _ => handshake.release()
    pendingResponseHandlers.put(execId, handleHandshakeResponse)
    if handshake.initiateFailed(initCommand(token, execId)) then
      pendingResponseHandlers.remove(execId)

  /** The handshake, carrying the token the server is asked to accept. */
  private def initCommand(tkn: Option[String], execId: String): InitCommand =
    val skipAnalysis = true
    val opts = InitializeOption(
      token = tkn,
      skipAnalysis = Some(skipAnalysis),
      canWork = Some(true),
      subscribeToAll = Some(false),
    )
    InitCommand(
      token = tkn, // duplicated with opts for compatibility
      execId = Option(execId),
      skipAnalysis = Some(skipAnalysis), // duplicated with opts for compatibility
      initializationOptions = Some(opts),
    )

  def init(promptCompleteUsers: Boolean, retry: Boolean): ServerSession =
    val conn = initImpl(promptCompleteUsers = promptCompleteUsers, retry = retry)
    connectionHolder.set(conn)
    conn

  private def bootSocketOpt(bootSocketName: String, namedPipeName: String): Option[Socket] =
    Try(ClientSocket.bootSocket(bootSocketName)).toOption match
      case Some(x)                => Some(x)
      case None if Util.isWindows =>
        Try(ClientSocket.localSocket(namedPipeName, useJNI)).toOption
      case _ => None

  private def connectTimeout: FiniteDuration =
    sys.env
      .get("SBT_CLIENT_CONNECT_TIMEOUT")
      .flatMap(_.toIntOption)
      .map(_.seconds)
      .getOrElse(5.minutes)
  private var connectDeadlineExpired = false

  /**
   * Forks another instance of sbt in the background.
   * This instance must be shutdown explicitly via `sbt -client shutdown`
   */
  def waitForServer(portfile: File, log: Boolean, startServer: Boolean): Unit =
    val base = arguments.baseDirectory.toPath.toRealPath()
    val target = base.resolve("project").resolve("target")
    val hash = HashUtil.farmHash(target.toString().getBytes("UTF-8"))
    val bootSocketName = BootServerSocket.socketLocation(base, hash)
    val namedPipeName = BootServerSocket.namedPipeLocation(hash)

    /*
     * For unknown reasons, linux sometimes struggles to connect to the socket in some
     * scenarios.
     */
    var socket: Option[Socket] = bootSocketOpt(bootSocketName, namedPipeName)
    val term = Terminal.console
    term.exitRawMode()
    var serverStderrFile: Option[File] = None
    val process = socket match
      case None if startServer =>
        if log then
          startupMessages.foreach: msg =>
            console.appendLog(Level.Info, msg)
        val props =
          Seq(
            term.getWidth,
            term.getHeight,
            term.isAnsiSupported,
            term.isColorEnabled,
            term.isSupershellEnabled
          ).mkString(",")

        if log && arguments.sbtLaunchJar.isDefined then
          val sbtScript = if Properties.isWin then "sbt.bat" else "sbt"
          console.appendLog(Level.Warn, s"server is started using sbt-launch jar directly")
          console.appendLog(
            Level.Warn,
            "this is not the recommended way: .sbtopts and .jvmopts files are not loaded and SBT_OPTS is ignored"
          )
          console.appendLog(
            Level.Warn,
            s"either upgrade $sbtScript to its latest version or make sure it is accessible from $$PATH, and run 'sbt bspConfig'"
          )
        val cmd = NetworkClient.serverCommand(arguments)

        // https://github.com/sbt/sbt/issues/6271
        val nohup =
          if Util.isEmacs && !Util.isWindows then List("nohup")
          else Nil

        // https://github.com/sbt/sbt/issues/8442
        // On Linux, if stdout/stderr are inherited and the buffer fills up (~64KB),
        // the server process will block on writes. Redirect to files instead of
        // inheriting or piping to avoid buffer deadlocks while still capturing
        // errors for diagnostics (https://github.com/sbt/sbt/issues/8812).
        val nullFile = new File(if Util.isWindows then "NUL" else "/dev/null")
        val stderrFile = Files.createTempFile("sbt-server-err", ".log").toFile
        stderrFile.deleteOnExit()
        serverStderrFile = Some(stderrFile)
        val processBuilder =
          new ProcessBuilder((nohup ++ cmd)*)
            .directory(arguments.baseDirectory)
            .redirectInput(Redirect.PIPE)
            .redirectOutput(nullFile)
            .redirectError(stderrFile)
        processBuilder.environment.put(Terminal.TERMINAL_PROPS, props)
        if arguments.forwardsSysProps then
          processBuilder.environment.put(
            NetworkClient.sysPropsEnv,
            NetworkClient.recordedSysProps(arguments.sbtArguments)
          )
          processBuilder.environment
            .put(NetworkClient.sysPropsPortfileEnv, portfile.getCanonicalPath)
        else
          Util.ignoreResult(processBuilder.environment.remove(NetworkClient.sysPropsEnv))
          Util.ignoreResult(processBuilder.environment.remove(NetworkClient.sysPropsPortfileEnv))
        Try(processBuilder.start()) match
          case Success(process) =>
            sbtProcess.set(process)
            Some(process)
          case Failure(e) =>
            if log then console.appendLog(Level.Error, s"Failed to start server : $e")
            throw new ServerFailedException
      case _ =>
        if log then console.appendLog(Level.Info, "sbt server is booting up")
        None
    if !startServer then
      val deadline = 5.seconds.fromNow
      while socket.isEmpty && !deadline.isOverdue() do
        socket = bootSocketOpt(bootSocketName, namedPipeName)
        if socket.isEmpty then Thread.sleep(20)
    val shutdown = new Thread(() => Option(sbtProcess.get).foreach(_.destroyForcibly()))
    Runtime.getRuntime.addShutdownHook(shutdown)
    var gotInputBack = false
    val readThreadAlive = new AtomicBoolean(true)
    /*
     * Socket.getInputStream.available doesn't always return a value greater than 0
     * so it is necessary to read the process output from the socket on a background
     * thread.
     */
    val readThread = new Thread("client-read-thread"):
      setDaemon(true)
      start()
      override def run(): Unit =
        try
          val buffer = mutable.ArrayBuffer.empty[Byte]
          while readThreadAlive.get do
            if socket.isEmpty then socket = bootSocketOpt(bootSocketName, namedPipeName)
            socket.foreach { s =>
              try
                s.getInputStream.read match
                  case -1 | 0 => readThreadAlive.set(false)
                  case 2      => // STX: start of text
                    gotInputBack = true
                  case 5 => // ENQ: enquiry
                    term.enterRawMode(); startInputThread()
                  case 3 if gotInputBack => // ETX: end of text
                    readThreadAlive.set(false)
                  case i if gotInputBack => stdinBytes.offer(i)
                  case 10                => // CR
                    buffer.append(10.toByte)
                    printStream.write(buffer.toArray[Byte])
                    buffer.clear()
                  case i =>
                    buffer.append(i.toByte)
              catch
                case e @ (_: IOException | _: InterruptedException) =>
                  readThreadAlive.set(false)
            }
            if socket.isEmpty && readThreadAlive.get then
              try Thread.sleep(10)
              catch
                case _: InterruptedException =>
          end while
        catch case e: IOException => e.printStackTrace(System.err)
    val connectDeadline = connectTimeout.fromNow
    @tailrec
    def blockUntilStart(): Unit =
      val stop =
        try
          socket match
            case None =>
              process.foreach { p =>
                val output = p.getInputStream
                while output.available > 0 do printStream.write(output.read())
              }
            case Some(s) =>
              while !gotInputBack && !stdinBytes.isEmpty && socket.isDefined do
                val out = s.getOutputStream
                val b = stdinBytes.poll
                if b == -1 then
                  // server waits for user input but stinBytes has ended
                  shutdown.run()
                else
                  out.write(b)
                  out.flush()
          process.foreach { p =>
            val error = p.getErrorStream
            while error.available > 0 do errorStream.write(error.read())
          }
          false
        catch case e: IOException => true
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
      if !portfile.exists && !stop && existsValidProcess && !connectDeadline.isOverdue() then
        blockUntilStart()
      else
        connectDeadlineExpired = connectDeadline.isOverdue() && !portfile.exists
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
    end blockUntilStart

    try blockUntilStart()
    catch case t: Throwable => t.printStackTrace()
    finally
      sbtProcess.set(null)
      Util.ignoreResult(Runtime.getRuntime.removeShutdownHook(shutdown))
    if !portfile.exists() then
      if connectDeadlineExpired then
        errorStream.write(
          s"sbt server did not start within ${connectTimeout.toSeconds} seconds\n".getBytes("UTF-8")
        )
        errorStream.flush()
      // Print captured server stderr so users can see why the server failed to start
      for errFile <- serverStderrFile do
        try
          try
            val bytes = Files.readAllBytes(errFile.toPath)
            if bytes.nonEmpty then
              errorStream.write(bytes)
              errorStream.flush()
          catch
            case _: Exception =>
        finally errFile.delete()
      throw new ServerFailedException
    // Clean up stderr temp file on successful startup
    serverStderrFile.foreach(_.delete())
    if attached.get && !stdinBytes.isEmpty then inputThread.drain()
  end waitForServer

  /** Called on the response for a returning message. */
  def onReturningResponse(msg: JsonRpcResponseMessage): Unit =
    def printResponse(): Unit =
      msg.result match
        case Some(result) =>
          // ignore result JSON
          console.success("completed")
        case _ =>
          msg.error match
            case Some(err) =>
              // ignore err details
              console.appendLog(Level.Error, "completed")
            case _ => // ignore
    printResponse()

  private def getExitCode(jvalue: Option[JValue]): Integer = jvalue match
    case Some(o: JObject) =>
      o.value
        .collectFirst {
          case v if v.field == "exitCode" =>
            Converter.fromJson[Integer](v.value).getOrElse(Integer.valueOf(1))
        }
        .getOrElse(1)
    case _ => 1

  private def handleAttach(msg: JsonRpcResponseMessage): Boolean =
    if attachUUID.get == msg.id then
      attachUUID.set(null)
      attached.set(true)
      inputThread.drain()
      true
    else false

  private def completeExec(execId: String, fExitCode: => Integer): Boolean =
    pendingResults.remove(execId) match
      case null                 => false
      case (q, startTime, name) =>
        val message = NetworkClient.elapsedString(startTime, System.currentTimeMillis)
        val exitCode = fExitCode
        if batchMode.get || !attached.get then
          if exitCode == 0 then console.success(message)
          else console.appendLog(Level.Error, message)
        q.offer(exitCode)
        true

  private def handleCompletion(handler: CompletionResponse => Unit)(
      msg: JsonRpcResponseMessage
  ): Unit =
    val emptyResponse = CompletionResponse(Vector.empty[String])
    val response = msg.result match
      case Some(o: JObject) =>
        o.value.foldLeft(emptyResponse) { (resp, i) =>
          if i.field == "items" then
            resp.withItems(
              Converter
                .fromJson[Vector[String]](i.value)
                .getOrElse(Vector.empty[String])
            )
          else if i.field == "cachedTestNames" then
            resp.withCachedTestNames(
              Converter.fromJson[Boolean](i.value).getOrElse(true)
            )
          else if i.field == "cachedMainClassNames" then
            resp.withCachedMainClassNames(
              Converter.fromJson[Boolean](i.value).getOrElse(true)
            )
          else resp
        }
      case _ => emptyResponse
    handler(response)
  end handleCompletion

  // cache the composed plan
  def onResponse(msg: JsonRpcResponseMessage): Unit =
    pendingResponseHandlers.remove(msg.id) match
      case null    => Util.ignoreResult(responseHandlers.exists(_(msg)))
      case handler => handler(msg)

  private val responseHandlers: Seq[JsonRpcResponseMessage => Boolean] = Seq(
    msg => completeExec(msg.id, getExitCode(msg.result)),
    handleAttach,
  )

  def onNotification(msg: JsonRpcNotificationMessage): Unit =
    def splitToMessage: Vector[(Level.Value, String)] =
      (msg.method, msg.params) match
        case ("build/logMessage", Some(json)) =>
          if !attached.get then
            import sbt.internal.langserver.codec.JsonProtocol.given
            Converter.fromJson[LogMessageParams](json) match
              case Success(params) => splitLogMessage(params)
              case Failure(_)      => Vector()
          else Vector()
        case (`systemOut`, Some(json)) =>
          Converter.fromJson[Array[Byte]](json) match
            case Success(bytes) if bytes.nonEmpty && attached.get =>
              synchronized(printStream.write(bytes))
            case _ =>
          Vector.empty
        case (`systemErr`, Some(json)) =>
          Converter.fromJson[Array[Byte]](json) match
            case Success(bytes) if bytes.nonEmpty && attached.get =>
              synchronized(errorStream.write(bytes))
            case _ =>
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
          import sbt.internal.langserver.codec.JsonProtocol.given
          Converter.fromJson[PublishDiagnosticsParams](json) match
            case Success(params) => splitDiagnostics(params); Vector()
            case Failure(_)      => Vector()
        case (`clientJob`, Some(json)) =>
          import sbt.internal.worker.codec.JsonProtocol.given
          Converter.fromJson[ClientJobParams](json) match
            case Success(params) =>
              clientSideRun(params) match
                case Success(_) =>
                  if interactive then console.success("ok")
                  else ()
                  Vector.empty
                case Failure(e) =>
                  if interactive then
                    Vector(
                      (Level.Error, e.getMessage)
                    )
                  else throw e
            case Failure(_) => Vector.empty
        case (`Shutdown`, Some(_))                => Vector.empty
        case (msg, _) if msg.startsWith("build/") => Vector.empty
        case ("sbt/exec", Some(json))             =>
          import sbt.protocol.codec.JsonProtocol.given
          Converter.fromJson[ExecStatusEvent](json) match
            case Success(event) if event.status == "Queued" =>
              event.message.foreach(m => errorStream.println(s"[info] $m"))
              Vector.empty
            case _ => Vector.empty
        case _ =>
          Vector(
            (
              Level.Warn,
              s"unknown event: ${msg.method} " + Serialization.compactPrintJsonOpt(msg.params)
            )
          )
    splitToMessage foreach { (level, msg) =>
      console.appendLog(level, msg)
    }
  end onNotification

  def splitLogMessage(params: LogMessageParams): Vector[(Level.Value, String)] =
    val level = messageTypeToLevel(params.`type`)
    if level == Level.Debug then Vector()
    else Vector((level, params.message))

  def messageTypeToLevel(severity: Long): Level.Value =
    severity match
      case MessageType.Error   => Level.Error
      case MessageType.Warning => Level.Warn
      case MessageType.Info    => Level.Info
      case MessageType.Log     => Level.Debug

  def splitDiagnostics(params: PublishDiagnosticsParams): Vector[(Level.Value, String)] =
    val uri = new URI(params.uri)
    val f = IO.toFile(uri)

    params.diagnostics map { d =>
      val level = d.severity match
        case Some(severity) => messageTypeToLevel(severity)
        case _              => Level.Error
      val line = d.range.start.line + 1
      val offset = d.range.start.character + 1
      val msg = s"$f:$line:$offset: ${d.message}"
      (level, msg)
    }

  private def clientSideRun(params: ClientJobParams): Try[Unit] =
    params.runInfo match
      case Some(info) => clientSideRun(info)
      case _          => Failure(new MessageOnlyException(s"runInfo is not specified in $params"))

  private def setWindowTitle(title: String): Unit =
    if System.console() != null && System.getenv("TERM") != null then
      Console.print(s"\u001b]0;$title\u0007")
      Console.flush()

  private def clientSideRun(runInfo: RunInfo): Try[Unit] =
    runInfo.windowTitle.foreach(setWindowTitle)
    def nativeRun(info: NativeRunInfo): Try[Unit] =
      import java.lang.ProcessBuilder as JProcessBuilder
      val option = ForkOptions(
        javaHome = None,
        outputStrategy = None, // TODO: Handle buffered output etc
        bootJars = Vector.empty,
        workingDirectory = info.workingDirectory.map(new File(_)),
        runJVMOptions = Vector.empty,
        connectInput = info.connectInput,
        envVars = RunHandler.mergedEnvVars(info.environmentVariables),
      )
      val command = info.cmd :: info.args.toList
      val jpb = new JProcessBuilder(command*)
      val exitCode =
        try Fork.blockForExitCode(Fork.forkInternal(option, Nil, jpb))
        catch
          case _: InterruptedException =>
            log.warn("run canceled")
            1
      Run.processExitCode(exitCode, "runner")
    inClientSideRun.set(true)
    try
      if runInfo.jvm then
        RunHandler.jvmRun(runInfo.jvmRunInfo.getOrElse(sys.error("missing jvmRunInfo")), log)
      else nativeRun(runInfo.nativeRunInfo.getOrElse(sys.error("missing nativeRunInfo")))
    finally inClientSideRun.set(false)
  end clientSideRun

  def onRequest(msg: JsonRpcRequestMessage): Unit =
    import sbt.protocol.codec.JsonProtocol.given
    (msg.method, msg.params) match
      case (`terminalCapabilities`, Some(json)) =>
        Converter.fromJson[TerminalCapabilitiesQuery](json) match
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
        Converter.fromJson[TerminalSetAttributesCommand](json) match
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
        Converter.fromJson[TerminalSetSizeCommand](json) match
          case Success(size) =>
            Terminal.console.setSize(size.width, size.height)
            sendCommandResponse("", TerminalSetSizeResponse(), msg.id)
          case Failure(_) =>
      case (`terminalSetEcho`, Some(json)) =>
        Converter.fromJson[TerminalSetEchoCommand](json) match
          case Success(echo) =>
            Terminal.console.setEchoEnabled(echo.toggle)
            sendCommandResponse("", TerminalSetEchoResponse(), msg.id)
          case Failure(_) =>
      case (`terminalSetRawMode`, Some(json)) =>
        Converter.fromJson[TerminalSetRawModeCommand](json) match
          case Success(raw) =>
            if raw.toggle then Terminal.console.enterRawMode()
            else Terminal.console.exitRawMode()
            sendCommandResponse("", TerminalSetRawModeResponse(), msg.id)
          case Failure(_) =>
      case _ =>
    end match
  end onRequest

  def connect(promptCompleteUsers: Boolean): Boolean =
    try
      init(promptCompleteUsers, retry = true)
      true
    catch
      case _: ServerFailedException =>
        console.appendLog(Level.Error, "failed to connect to server")
        false

  private val contHandler: () => Unit = () =>
    if Terminal.console.getLastLine.nonEmpty then
      printStream.print(ConsoleAppender.DeleteLine + Terminal.console.getLastLine.get)
  private def withSignalHandler[R](handler: () => Unit, sig: String)(f: => R): R =
    val registration = Signals.register(handler, sig)
    try f
    finally registration.remove()
  private val cancelled = new AtomicBoolean(false)

  def run(): Int =
    withSignalHandler(contHandler, Signals.CONT) {
      interactiveThread.set(Thread.currentThread)
      val cleaned = arguments.commandArguments
      val userCommands = arguments.commandArguments.takeWhile(_ != TerminateAction)
      val exit = arguments.commandArguments.nonEmpty && userCommands.isEmpty
      attachUUID.set(sendJson(attach, s"""{"interactive": $interactive}"""))
      val handler: () => Unit = () =>
        def exitAbruptly() =
          exitClean.set(false)
          close()
        if inClientSideRun.get() then ()
        else if cancelled.compareAndSet(false, true) then
          val cancelledTasks =
            val queue = sendCancelAllCommand()
            Option(queue.poll(1, TimeUnit.SECONDS)).getOrElse(true)
          if (batchMode.get && pendingResults.isEmpty) || !cancelledTasks then exitAbruptly()
          else cancelled.set(false)
        else exitAbruptly() // handles double ctrl+c to force a shutdown
      withSignalHandler(handler, Signals.INT) {
        def block(): Int =
          try this.synchronized(this.wait())
          catch
            case _: InterruptedException =>
          if exitClean.get then 0 else 1
        if interactive then block()
        else if exit then 0
        else
          batchMode.set(true)
          val res = batchExecute(userCommands.toList)
          if !batchMode.get then block() else res
      }
    }

  def batchExecute(userCommands: List[String]): Int =
    val cmd = userCommands.mkString(" ")
    sendAndWait(cmd, None)

  def getCompletions(query: String): Seq[String] =
    val quoteCount = query.foldLeft(0) {
      case (count, '"') => count + 1
      case (count, _)   => count
    }
    val inQuote = quoteCount % 2 != 0
    val (rawPrefix, prefix, rawSuffix, suffix) = if quoteCount > 0 then
      query.lastIndexOf('"') match
        case -1 => (query, query, None, None) // shouldn't happen
        case i  =>
          val rawPrefix = query.substring(0, i)
          val prefix = rawPrefix.replace("\"", "").replace("\\;", ";")
          val rawSuffix = query.substring(i).replace("\\;", ";")
          val suffix = if rawSuffix.length > 1 then rawSuffix.substring(1) else ""
          (rawPrefix, prefix, Some(rawSuffix), Some(suffix))
    else (query, query.replace("\\;", ";"), None, None)
    val tailSpace = query.endsWith(" ") || query.endsWith("\"")
    val sanitizedQuery = suffix.foldLeft(prefix) { _ + _ }
    def getCompletions(query: String, sendCommand: Boolean): Seq[String] =
      val result = new LinkedBlockingQueue[CompletionResponse]()
      val json = s"""{"query":"$query","level":1}"""
      val execId = sendJson("sbt/completion", json)
      pendingResponseHandlers.put(execId, handleCompletion(result.put))
      val response = result.poll(30, TimeUnit.SECONDS) match
        case null => throw new TimeoutException("no response from server within 30 seconds")
        case r    => r
      def fillCompletions(label: String, regex: String, command: String): Seq[String] =
        def updateCompletions(): Seq[String] =
          errorStream.println()
          sendJson(attach, s"""{"interactive": false}""")
          sendAndWait(query.replaceAll(regex + ".*", command).trim, None)
          getCompletions(query, false)
        if noStdErr then Nil
        else if noTab then updateCompletions()
        else
          errorStream.print(s"\nNo cached $label names found. Press '<tab>' to compile: ")
          startInputThread()
          stdinBytes.poll(5, TimeUnit.SECONDS) match
            case null        => Nil
            case i if i == 9 => updateCompletions()
            case _           => Nil
      val testNameCompletions =
        if !response.cachedTestNames.getOrElse(true) && sendCommand then
          fillCompletions("test", "test(Only|Quick)", "definedTestNames")
        else Nil
      val classNameCompletions =
        if !response.cachedMainClassNames.getOrElse(true) && sendCommand then
          fillCompletions("main class", "runMain", "discoveredMainClasses")
        else Nil
      val completions = response.items
      testNameCompletions ++ classNameCompletions ++ completions
    end getCompletions
    getCompletions(sanitizedQuery, true) collect {
      case c if inQuote                      => c
      case c if tailSpace && c.contains(" ") => c.replace(prefix, "")
      case c if !tailSpace                   => c.split(" ").last
    }
  end getCompletions

  private def sendAndWait(cmd: String, limit: Option[Deadline]): Int =
    val queue = sendExecCommand(cmd)
    var result: Integer = null
    while running.get && result == null && limit.fold(true)(!_.isOverdue()) do
      try
        result = limit match
          case Some(l) => queue.poll((l - Deadline.now).toMillis, TimeUnit.MILLISECONDS)
          case _       => queue.take
      catch
        case _: InterruptedException if cmd == Shutdown => result = 0
        case _: InterruptedException                    => result = if exitClean.get then 0 else 1
    if result == null then 1 else result

  def sendExecCommand(commandLine: String): LinkedBlockingQueue[Integer] =
    val execId = UUID.randomUUID.toString
    val queue = new LinkedBlockingQueue[Integer]
    sendCommand(ExecCommand(commandLine, execId))
    pendingResults.put(execId, (queue, System.currentTimeMillis, commandLine))
    queue

  def sendCancelAllCommand(): LinkedBlockingQueue[Boolean] =
    val queue = new LinkedBlockingQueue[Boolean]
    val execId = sendJson(cancelRequest, s"""{"id":"$CancelAll"}""")
    pendingResponseHandlers.put(execId, msg => queue.offer(msg.toString.contains("Task cancelled")))
    queue

  def sendCommand(command: CommandMessage): Unit =
    try
      connection.sendCommand(command)
      lock.synchronized {
        status.set("Processing")
      }
    catch
      case e: SocketException if command.toString.contains("exit") => running.set(false)
      case e: IOException                                          =>
        errorStream.println(s"Caught exception writing command to server: $e")
        running.set(false)
  def sendCommandResponse(method: String, command: EventMessage, id: String): Unit =
    import sbt.protocol.codec.JsonProtocol.given
    try connection.sendJsonRpcResponse(id, command)
    catch
      case e: IOException =>
        errorStream.println(s"Caught exception writing command to server: $e")
        running.set(false)
  def sendJson(method: String, params: String): String =
    val uuid = UUID.randomUUID.toString
    sendJson(method, params, uuid)
    uuid
  def sendJson(method: String, params: String, uuid: String): Unit =
    connection.sendJsonRpcRaw(uuid, method, params)

  def sendNotification(method: String, params: String): Unit =
    connection.sendJsonRpcNotificationRaw(method, params)

  override def close(): Unit =
    try
      running.set(false)
      stdinBytes.offer(-1)
      val mainThread = interactiveThread.getAndSet(null)
      if mainThread != null && mainThread != Thread.currentThread then mainThread.interrupt
      if connectionHolder.get ne null then
        try sendExecCommand("exit")
        finally connectionHolder.close()
      inputThread.close()
    catch
      case t: Throwable =>
        t.printStackTrace()
        throw t

  /**
   * Reads stdin on behalf of the server, which asks for it one byte at a time via
   * `readSystemIn`/`cancelReadSystemIn` notifications. The design here answers two problems:
   *
   *   - (2020, #5828/#5863/#5856) Switching the terminal between raw and canonical mode can't
   *     happen while a read is blocked on it. So a read must exist only for as long as the
   *     server has actually asked for a byte, never sitting on the terminal unrequested.
   *   - (2026, #9507) Satisfying that by spawning a thread per byte that exits once forwarded
   *     races the next request against that exit: a `readSystemIn` arriving mid-exit is silently
   *     dropped, and since nothing else will ever ask for that byte again, the session stops
   *     accepting input.
   */
  private class RawInputThread extends Thread("sbt-read-input-thread") with AutoCloseable:
    setDaemon(true)
    private val stopped = AtomicBoolean(false)
    private val readGate = Semaphore(0)
    start()

    override final def run(): Unit =
      while !stopped.get do
        try
          readGate.acquire()
          if !stopped.get then
            val b = inputStream.read
            inLock.synchronized(stdinBytes.offer(b))
            if attached.get() then drain()
            if b == -1 then stopped.set(true)
        catch case _: InterruptedException | NonFatal(_) => ()

    def request(): Unit = readGate.release()
    def cancel(): Unit = interrupt()
    def drain(): Unit = inLock.synchronized {
      while !stdinBytes.isEmpty do
        val byte = stdinBytes.poll()
        sendNotification(systemIn, byte.toString)
    }

    override def close(): Unit =
      stopped.set(true)
      readGate.release()
      RawInputThread.this.interrupt()
  end RawInputThread
end NetworkClient

object NetworkClient:
  private[sbt] val CancelAll = "__CancelAll"
  private[sbt] val handshakeAttemptLimit = 3
  private def consoleAppenderInterface(printStream: PrintStream): ConsoleInterface =
    val appender = ConsoleAppender("thin", ConsoleOut.printStreamOut(printStream))
    new ConsoleInterface:
      override def appendLog(level: Level.Value, message: => String): Unit =
        appender.appendLog(level, message)
      override def success(msg: String): Unit = appender.success(msg)
  private def simpleConsoleInterface(
      doPrintln: String => Unit,
      useColor: Boolean
  ): ConsoleInterface =
    new ConsoleInterface:
      import scala.Console.{ GREEN, RED, RESET, YELLOW }
      override def appendLog(level: Level.Value, message: => String): Unit = synchronized {
        val prefix =
          if useColor then
            level match
              case Level.Error => s"[$RED$level$RESET]"
              case Level.Warn  => s"[$YELLOW$level$RESET]"
              case _           => s"[$RESET$level$RESET]"
          else s"[$level]"
        message.linesIterator.foreach(line => doPrintln(s"$prefix $line"))
      }
      override def success(msg: String): Unit =
        if useColor then doPrintln(s"[${GREEN}success$RESET] $msg")
        else doPrintln(s"[success] $msg")
  private[client] class Arguments(
      val baseDirectory: File,
      val sbtArguments: Seq[String],
      val commandArguments: Seq[String],
      val completionArguments: Seq[String],
      val sbtScript: String,
      val bsp: Boolean,
      val sbtLaunchJar: Option[String],
      val launcherValueArgs: Seq[String] = Nil,
      // false when -D options went to the JVM running this client instead of its arguments,
      // in which case they say nothing about the server
      val forwardsSysProps: Boolean = true,
  ):
    def withBaseDirectory(file: File): Arguments =
      copy(baseDirectory = file)
    def withoutSysProps: Arguments =
      copy(forwardsSysProps = false)
    private def copy(
        baseDirectory: File = baseDirectory,
        forwardsSysProps: Boolean = forwardsSysProps
    ): Arguments =
      new Arguments(
        baseDirectory,
        sbtArguments,
        commandArguments,
        completionArguments,
        sbtScript,
        bsp,
        sbtLaunchJar,
        launcherValueArgs,
        forwardsSysProps,
      )
  end Arguments
  private[client] def serverCommand(arguments: Arguments): List[String] =
    arguments.sbtLaunchJar match
      case Some(lj) =>
        val java =
          Option(Properties.javaHome).map(javaHome => s"$javaHome/bin/java").getOrElse("java")
        List(java) ++ arguments.sbtArguments.filterNot(emptyBuildFlags.contains) ++
          List("-jar", lj, DashDashDetachStdio, DashDashServer)
      case _ =>
        List(arguments.sbtScript) ++ arguments.launcherValueArgs ++ arguments.sbtArguments ++
          List(DashDashDetachStdio, DashDashServer)

  /** Carries the `-D` options a client forwards to the server it starts. */
  private[sbt] val sysPropsEnv = "SBT_SERVER_SYS_PROPS"

  /**
   * The connection file of the server [[sysPropsEnv]] is meant for. Both are inherited by
   * everything the server itself starts, so a server only trusts them when they name it.
   */
  private[sbt] val sysPropsPortfileEnv = "SBT_SERVER_SYS_PROPS_PORTFILE"
  private[sbt] val serverShutdownTimeout: FiniteDuration = 30.seconds

  /** How long a server that never took the shutdown request is given to disappear anyway. */
  private[sbt] val serverShutdownGrace: FiniteDuration = 2.seconds

  /**
   * These are set per client invocation, so they don't describe the server JVM.
   * `sbt.io.virtual` belongs here because the client appends its own `=true` either way.
   */
  private[client] val ignoredSysProps: Set[String] = Set(
    "sbt.banner",
    "sbt.client",
    "sbt.color",
    "sbt.io.virtual",
    "sbt.log.noformat",
    "sbt.script",
    "sbt.server.autorestart",
    "sbt.server.autostart",
    "sbt.supershell",
  )

  /** The name a `-D` option defines, which is everything up to the first `=`. */
  private[client] def sysPropName(option: String): String =
    option.drop(2).takeWhile(_ != '=')

  /** The `-D` options that make up the identity of a server started with `sbtArguments`. */
  private[client] def serverSysProps(sbtArguments: Seq[String]): Seq[String] =
    sbtArguments
      .filter(_.startsWith("-D"))
      .filterNot(a => ignoredSysProps(sysPropName(a)))
      // a name given twice is whatever the JVM ends up with, which is the last definition
      .foldLeft(TreeMap.empty[String, String])((acc, a) => acc.updated(sysPropName(a), a))
      .values
      .toVector

  private lazy val random = new SecureRandom

  /** The digest a connection file records `option` as, under a salt of its own. */
  private def sysPropDigest(salt: String, option: String): String =
    val md = MessageDigest.getInstance("SHA-256")
    md.update(salt.getBytes(UTF_8))
    md.update(option.getBytes(UTF_8))
    s"$salt:${Hash.toHex(md.digest)}"

  /**
   * The `-D` options as they are written down: the name each defines, and a salted digest
   * of the option itself. A value can carry a credential, and a name is enough to tell one
   * server from another and to say which options changed.
   */
  private[sbt] def digestSysProps(props: Seq[String]): Vector[String] =
    props.toVector.map { p =>
      val salt = new Array[Byte](8)
      random.nextBytes(salt)
      s"${sysPropName(p)}=${sysPropDigest(Hash.toHex(salt), p)}"
    }

  /**
   * Carries the recorded options to the server one per line. They are encoded because a
   * value is free to contain a newline, which would otherwise read back as two options.
   */
  private[sbt] def encodeSysProps(recorded: Seq[String]): String =
    recorded.map(r => Base64.getEncoder.encodeToString(r.getBytes(UTF_8))).mkString("\n")

  /** Reads back the options [[sysPropsEnv]] carries to the server they were passed to. */
  private[sbt] def decodeSysProps(value: String): Vector[String] =
    value
      .split("\n")
      .toVector
      .filter(_.nonEmpty)
      .flatMap(r => Try(new String(Base64.getDecoder.decode(r), UTF_8)).toOption)

  /** What [[sysPropsEnv]] carries to a server started with `sbtArguments`. */
  def recordedSysProps(sbtArguments: Seq[String]): String =
    encodeSysProps(digestSysProps(serverSysProps(sbtArguments)))

  /**
   * How the options a server recorded and the ones a client carries differ: the names the
   * client no longer passes, the ones it adds, and the ones it gives another value. Names in
   * `deferred` are nobody's to answer for and are left out of all three.
   */
  private[client] def sysPropsDiff(
      recorded: Seq[String],
      current: Seq[String],
      deferred: Set[String] = Set.empty
  ): (Seq[String], Seq[String], Seq[String]) =
    val was = recorded
      .map(r => r.takeWhile(_ != '=') -> r.dropWhile(_ != '=').drop(1))
      .toMap
      .removedAll(deferred)
    val now = current.map(o => sysPropName(o) -> o).toMap.removedAll(deferred)
    val changed = (was.keySet & now.keySet).filter { name =>
      val digest = was(name)
      sysPropDigest(digest.takeWhile(_ != ':'), now(name)) != digest
    }
    (
      (was.keySet -- now.keySet).toSeq.sorted,
      (now.keySet -- was.keySet).toSeq.sorted,
      changed.toSeq.sorted
    )

  private[client] val completions = "--completions"
  private[client] val noTab = "--no-tab"
  private[client] val noStdErr = "--no-stderr"
  private[client] val sbtBase = "--sbt-base-directory"
  // Launcher flags that take a value argument (flag + next arg should be skipped)
  private[client] val launcherValueFlags: Set[String] = Set(
    "-mem",
    "--mem",
    "-jvm-debug",
    "--jvm-debug",
    "-sbt-jar",
    "--sbt-jar",
    "-sbt-cache",
    "--sbt-cache",
    "-sbt-version",
    "--sbt-version",
    "-java-home",
    "--java-home",
    "-ivy",
    "--ivy",
    "-sbt-boot",
    "--sbt-boot",
    "-sbt-dir",
    "--sbt-dir",
  )
  // Launcher flags that take no value (flag itself should be skipped)
  private[client] val launcherNoValueFlags: Set[String] = Set(
    "-client",
    "--client",
    "--server",
    "--jvm-client",
    "-h",
    "-help",
    "--help",
    "-v",
    "-verbose",
    "--verbose",
    "-V",
    "-version",
    "--version",
    "--numeric-version",
    "--script-version",
    "-d",
    "-debug",
    "--debug",
    "-debug-inc",
    "--debug-inc",
    "-batch",
    "--batch",
    "--no-hide-jdk-warnings",
    "-no-colors",
    "--no-colors",
    "-timings",
    "--timings",
    "-traces",
    "--traces",
    "-no-share",
    "--no-share",
    "-no-global",
    "--no-global",
    "shutdownall"
  )
  private[client] val emptyBuildFlags: Set[String] = Set(
    "-allow-empty",
    "--allow-empty",
    "-sbt-create",
    "--sbt-create",
  )
  // Prefixes for launcher flags using = syntax
  private[client] val launcherEqPrefixes: Seq[String] = Seq(
    "--supershell=",
    "-supershell=",
    "--color=",
    "-color=",
    "--autostart=",
    "-autostart=",
  )
  private[client] val launcherValueEqPrefixes: Seq[String] =
    launcherValueFlags.toSeq.map(_ + "=")
  private[client] def parseArgs(args: Array[String]): Arguments =
    val defaultSbtScript = if Properties.isWin then "sbt.bat" else "sbt"
    var sbtScript = Properties.propOrNone("sbt.script")
    var launchJar: Option[String] = None
    var bsp = false
    val commandArgs = new mutable.ArrayBuffer[String]
    val sbtArguments = new mutable.ArrayBuffer[String]
    val completionArguments = new mutable.ArrayBuffer[String]
    val launcherValueArgs = new mutable.ArrayBuffer[String]
    val SysProp = "-D([^=]+)=(.*)".r
    val sanitized = new mutable.ArrayBuffer[String]
    val splitFromPrev = new mutable.ArrayBuffer[Boolean]
    args.foreach {
      case a if a.startsWith("\"") =>
        sanitized += a
        splitFromPrev += false
      case a =>
        var first = true
        a.split(" ").foreach { part =>
          if part.nonEmpty then
            sanitized += part
            splitFromPrev += !first
            first = false
        }
    }
    def valueFrom(start: Int): (String, Int) =
      var last = start
      val sb = new StringBuilder(sanitized(start))
      while last + 1 < sanitized.length && splitFromPrev(last + 1) do
        last += 1
        sb.append(" ").append(sanitized(last))
      (sb.toString, last)
    var i = 0
    while i < sanitized.length do
      sanitized(i) match
        case a if completionArguments.nonEmpty                        => completionArguments += a
        case a if commandArgs.nonEmpty && emptyBuildFlags.contains(a) =>
          sbtArguments += a
        case a if commandArgs.nonEmpty                                     => commandArgs += a
        case a if a == noStdErr || a == noTab || a.startsWith(completions) =>
          completionArguments += a
        case a if a.startsWith("--sbt-script=") =>
          sbtScript = a
            .split("--sbt-script=")
            .lastOption
            .orElse(sbtScript)
        case "--sbt-script" if i + 1 < sanitized.length =>
          i += 1
          sbtScript = Some(sanitized(i))
        case a if a.startsWith("--sbt-launch-jar=") =>
          launchJar = a
            .split("--sbt-launch-jar=")
            .lastOption
            .map(_.replace("%20", " "))
        case "--sbt-launch-jar" if i + 1 < sanitized.length =>
          i += 1
          launchJar = Option(sanitized(i).replace("%20", " "))
        case "-bsp" | "--bsp" | "bsp"     => bsp = true
        case "-no-server" | "--no-server" =>
          System.setProperty("sbt.server.autostart", "false")
        case a if a.startsWith("--autostart=") =>
          System.setProperty("sbt.server.autostart", a.stripPrefix("--autostart="))
        case a if a.startsWith("-autostart=") =>
          System.setProperty("sbt.server.autostart", a.stripPrefix("-autostart="))
        case a if launcherValueFlags.contains(a) =>
          if i + 1 < sanitized.length then
            launcherValueArgs += a
            val (value, last) = valueFrom(i + 1)
            launcherValueArgs += value
            i = last
        case a if launcherValueEqPrefixes.exists(p => a.startsWith(p)) =>
          val (full, last) = valueFrom(i)
          i = last
          val eq = full.indexOf('=')
          if eq < full.length - 1 then
            launcherValueArgs += full.substring(0, eq)
            launcherValueArgs += full.substring(eq + 1)
        case a if launcherNoValueFlags.contains(a)                => ()
        case a if launcherEqPrefixes.exists(p => a.startsWith(p)) => ()
        case a if a.startsWith("-J")                              => ()
        case a if !a.startsWith("-")                              => commandArgs += a
        case a @ SysProp(key, value)                              =>
          System.setProperty(key, value)
          sbtArguments += a
        case a => sbtArguments += a
      end match
      i += 1
    end while
    val base = new File("").getCanonicalFile
    if !sbtArguments.contains("-Dsbt.io.virtual=true") then sbtArguments += "-Dsbt.io.virtual=true"
    if !sbtArguments.exists(_.startsWith("-Dsbt.script")) then
      sbtScript.foreach { sbtScript =>
        sbtArguments += s"-Dsbt.script=$sbtScript"
      }
    new Arguments(
      base,
      sbtArguments.toSeq,
      commandArgs.toSeq,
      completionArguments.toSeq,
      sbtScript.getOrElse(defaultSbtScript).replace("%20", " "),
      bsp,
      launchJar,
      launcherValueArgs.toSeq,
    )
  end parseArgs

  def elapsedString(startTime: Long, endTime: Long): String =
    s"elapsed time: ${elapsedStr(startTime, endTime)}"

  private def elapsedStr(startTime: Long, endTime: Long): String =
    val total = (endTime - startTime + 500) / 1000
    s"$total s" +
      (if total <= 60 then ""
       else
         val hours = total / 3600 match
           case 0 => "0"
           case h => f"$h%02d"
         val mins = f"${total % 3600 / 60}%02d"
         val secs = f"${total % 60}%02d"
         s" ($hours:$mins:$secs.0)")

  def client(
      baseDirectory: File,
      args: Array[String],
      inputStream: InputStream,
      printStream: PrintStream,
      errorStream: PrintStream,
      useJNI: Boolean
  ): Int =
    val client =
      simpleClient(
        NetworkClient.parseArgs(args).withBaseDirectory(baseDirectory),
        inputStream,
        printStream,
        errorStream,
        useJNI,
      )
    try
      if client.connect(promptCompleteUsers = false) then client.run()
      else 1
    catch
      case e: Exception =>
        client.logFailure(e)
        1
    finally client.close()
  end client
  def client(
      baseDirectory: File,
      args: Arguments,
      inputStream: InputStream,
      errorStream: PrintStream,
      terminal: Terminal,
      useJNI: Boolean
  ): Int =
    val printStream = if args.bsp then errorStream else terminal.printStream
    val client =
      simpleClient(
        args.withBaseDirectory(baseDirectory),
        inputStream,
        printStream,
        errorStream,
        useJNI,
      )
    clientImpl(client, args.bsp)
  private def clientImpl(client: NetworkClient, isBsp: Boolean): Int =
    try
      if isBsp then
        val (socket, _) =
          client.connectOrStartServerAndConnect(promptCompleteUsers = false, retry = true)
        BspClient.bspRun(socket)
      else if client.connect(promptCompleteUsers = false) then client.run()
      else 1
    catch
      case e: Exception =>
        client.logFailure(e)
        1
    finally client.close()
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
      printStream: PrintStream,
      errorStream: PrintStream,
      useJNI: Boolean,
  ): NetworkClient =
    val interface =
      NetworkClient.simpleConsoleInterface(printStream.println, Terminal.isColorEnabled)
    new NetworkClient(arguments, interface, inputStream, errorStream, printStream, useJNI)
  def main(args: Array[String]): Unit =
    val (jnaArg, restOfArgs) = args.partition(_ == "--jna")
    val useJNI = jnaArg.isEmpty
    val base = new File("").getCanonicalFile
    if restOfArgs.exists(_.startsWith(NetworkClient.completions)) then
      System.exit(complete(base, restOfArgs, useJNI, System.in, System.out))
    else
      val hook = new Thread(() =>
        System.out.print(ConsoleAppender.ClearScreenAfterCursor)
        System.out.flush()
      )
      Runtime.getRuntime.addShutdownHook(hook)
      val parsed = parseArgs(restOfArgs)
      System.exit(Terminal.withStreams(isServer = false, isSubProcess = false) {
        val term = Terminal.console
        try client(base, parsed, term.inputStream, System.err, term, useJNI)
        catch case _: AccessDeniedException => 1
        finally
          Runtime.getRuntime.removeShutdownHook(hook)
          hook.run()
      })
  end main
  def complete(
      baseDirectory: File,
      args: Array[String],
      useJNI: Boolean,
      in: InputStream,
      out: PrintStream
  ): Int =
    val cmd: String = args.find(_.startsWith(NetworkClient.completions)) match
      case Some(c) =>
        c.split('=').lastOption match
          case Some(query) =>
            query.indexOf(" ") match
              case -1 => throw new IllegalArgumentException(query)
              case i  => query.substring(i + 1)
          case _ => throw new IllegalArgumentException(c)
      case _ => throw new IllegalStateException("should be unreachable")
    val quiet = args.exists(_ == "--quiet")
    val errorStream = if quiet then new PrintStream(_ => {}, false) else System.err
    val sbtArgs = args.takeWhile(!_.startsWith(NetworkClient.completions))
    val arguments = NetworkClient.parseArgs(sbtArgs)
    val noTab = args.contains("--no-tab")
    try
      val client =
        simpleClient(
          arguments.withBaseDirectory(baseDirectory),
          inputStream = in,
          errorStream = errorStream,
          printStream = errorStream,
          useJNI = useJNI,
        )
      try
        val results =
          if client.connect(promptCompleteUsers = true) then client.getCompletions(cmd)
          else Nil
        out.println(results.sorted.distinct mkString "\n")
        0
      catch case _: Exception => 1
      finally client.close()
    catch case _: AccessDeniedException => 1
  end complete

  def run(configuration: xsbti.AppConfiguration, arguments: List[String]): Int =
    run(configuration, arguments, false)
  def run(
      configuration: xsbti.AppConfiguration,
      arguments: List[String],
      redirectOutput: Boolean
  ): Int =
    val term = Terminal.console
    val err = new PrintStream(term.errorStream)
    val out = if redirectOutput then err else new PrintStream(term.outputStream)
    val args =
      parseArgs(arguments.toArray).withoutSysProps.withBaseDirectory(configuration.baseDirectory)
    val useJNI =
      (Util.isMac && sys.props.getOrElse("os.arch", "") != "x86_64") ||
        System.getProperty("sbt.ipcsocket.jni", "false") == "true"
    val client = simpleClient(args, term.inputStream, out, err, useJNI = useJNI)
    clientImpl(client, args.bsp)
  private class AccessDeniedException extends Throwable
end NetworkClient
