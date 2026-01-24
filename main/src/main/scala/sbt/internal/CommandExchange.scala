/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

package internal
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.*
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }

import sbt.BasicCommandStrings.{
  Cancel,
  CompleteExec,
  Shutdown,
  TerminateAction,
  networkExecPrefix
}
import sbt.BasicKeys.*
import sbt.internal.protocol.JsonRpcResponseError
import sbt.internal.server.*
import sbt.internal.ui.UITask
import sbt.internal.util.*
import sbt.io.syntax.*
import sbt.io.{ Hash, IO }
import sbt.nio.Watch.NullLogger
import sbt.protocol.Serialization.{ attach, dropIfIdle }
import sbt.protocol.{ ExecStatusEvent, LogEvent }
import sbt.internal.protocol.{ JsonRpcNotificationMessage, PortFile }
import sbt.util.Logger
import sjsonnew.JsonFormat
import sjsonnew.support.scalajson.unsafe.{ Parser, Converter, CompactPrinter }

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

/**
 * The command exchange merges multiple command channels (e.g. network and console),
 * and acts as the central multiplexing point.
 * Instead of blocking on JLine.readLine, the server command will block on
 * this exchange, which could serve command request from either of the channel.
 */
private[sbt] final class CommandExchange {
  private val autoStartServerSysProp =
    sys.props get "sbt.server.autostart" forall (_.toLowerCase == "true")
  private var server: Option[ServerInstance] = None
  private val firstInstance: AtomicBoolean = new AtomicBoolean(true)
  private val monitoringActiveJson: AtomicBoolean = new AtomicBoolean(false)
  private val commandQueue: LinkedBlockingQueue[Exec] = new LinkedBlockingQueue[Exec]
  private val channelBuffer: ListBuffer[CommandChannel] = new ListBuffer()
  private val channelBufferLock = new AnyRef {}
  private val fastTrackChannelQueue = new LinkedBlockingQueue[FastTrackTask]
  private val nextChannelId: AtomicInteger = new AtomicInteger(0)
  private val lastState = new AtomicReference[State]
  private val currentExecRef = new AtomicReference[Exec]
  private val lastActivityTime = new AtomicReference[Long](System.currentTimeMillis())
  private[sbt] def hasServer = server.isDefined
  addConsoleChannel()

  def channels: List[CommandChannel] = channelBuffer.toList

  def subscribe(c: CommandChannel): Unit = channelBufferLock.synchronized {
    channelBuffer.append(c)
    c.register(commandQueue, fastTrackChannelQueue)
  }

  private[sbt] def withState[T](f: State => T): T = f(lastState.get)
  def blockUntilNextExec: Exec = blockUntilNextExec(Duration.Inf, NullLogger)
  // periodically move all messages from all the channels
  private[sbt] def blockUntilNextExec(interval: Duration, logger: Logger): Exec =
    blockUntilNextExec(interval, None, logger)
  private[sbt] def blockUntilNextExec(
      interval: Duration,
      state: Option[State],
      logger: Logger
  ): Exec = {
    val idleDeadline = state.flatMap { s =>
      lastState.set(s)
      s.get(BasicKeys.serverIdleTimeout) match {
        case Some(Some(d)) => Some(d.fromNow)
        case _             => None
      }
    }
    @tailrec def impl(gcDeadline: Option[Deadline], idleDeadline: Option[Deadline]): Exec = {
      state.foreach(s => prompt(ConsolePromptEvent(s)))
      def poll: Option[Exec] = {
        val deadline = gcDeadline.toSeq ++ idleDeadline match {
          case s @ Seq(_, _) => Some(s.min)
          case s             => s.headOption
        }
        try
          Option(deadline match {
            case Some(d: Deadline) =>
              commandQueue.poll(d.timeLeft.toMillis + 1, TimeUnit.MILLISECONDS) match {
                case null if idleDeadline.fold(false)(_.isOverdue) =>
                  state.foreach { s =>
                    s.get(BasicKeys.serverIdleTimeout) match {
                      case Some(Some(d)) => s.log.info(s"sbt idle timeout of $d expired")
                      case _             =>
                    }
                  }
                  Exec(TerminateAction, Some(CommandSource(ConsoleChannel.defaultName)))
                case x => x
              }
            case _ => commandQueue.take
          })
        catch { case _: InterruptedException => None }
      }
      poll match {
        case Some(exec) if exec.source.fold(true)(s => channels.exists(_.name == s.channelName)) =>
          exec.commandLine match {
            case `TerminateAction`
                if exec.source.fold(false)(_.channelName.startsWith("network")) =>
              channels.collectFirst {
                case c: NetworkChannel if exec.source.fold(false)(_.channelName == c.name) => c
              } match {
                case Some(c) if c.isAttached =>
                  c.shutdown(false)
                  impl(gcDeadline, idleDeadline)
                case _ => exec
              }
            case _ => exec
          }
        case Some(e) => e
        case None =>
          val newDeadline = if (gcDeadline.fold(false)(_.isOverdue())) {
            GCUtil.forceGcWithInterval(interval, logger)
            None
          } else gcDeadline
          impl(newDeadline, idleDeadline)
      }
    }
    // Do not manually run GC until the user has been idling for at least the min gc interval.
    impl(
      interval match {
        case d: FiniteDuration => Some(d.fromNow)
        case _                 => None
      },
      idleDeadline
    )
  }

  private def addConsoleChannel(): Unit =
    if Terminal.startedByRemoteClient then ()
    else
      val name = ConsoleChannel.defaultName
      subscribe(new ConsoleChannel(name, mkAskUser(name)))

  def run(s: State): State = run(s, s.get(autoStartServer).getOrElse(true))
  def run(s: State, autoStart: Boolean): State = {
    val startedByRemote = Terminal.startedByRemoteClient
    if (autoStartServerSysProp && (autoStart || startedByRemote)) runServer(s)
    else s
  }
  private[sbt] def setState(s: State): Unit = lastState.set(s)

  private def newNetworkName: String = s"network-${nextChannelId.incrementAndGet()}"

  private[sbt] def removeChannel(c: CommandChannel): Unit = {
    channelBufferLock.synchronized {
      Util.ignoreResult(channelBuffer -= c)
    }
    commandQueue.removeIf { e =>
      e.source.map(_.channelName) == Some(c.name) && e.commandLine != Shutdown
    }
    currentExec.withFilter(_.source.map(_.channelName) == Some(c.name)).foreach { e =>
      Util.ignoreResult(NetworkChannel.cancel(e.execId, e.execId.getOrElse("0"), force = false))
    }
    try commandQueue.put(Exec(s"${ContinuousCommands.stopWatch} ${c.name}", None))
    catch { case _: InterruptedException => }
    // When a client disconnects, notify other servers to drop if idle
    notifyOtherServersOnExit()
  }

  private def mkAskUser(
      name: String,
  ): (State, CommandChannel) => UITask = { (state, channel) =>
    ContinuousCommands
      .watchUITaskFor(state, channel)
      .getOrElse(new UITask.AskUserTask(state, channel))
  }

  private[sbt] def currentExec = Option(currentExecRef.get)

  /**
   * Check if a server instance is running already, and start one if it isn't.
   */
  private[sbt] def runServer(s: State): State = {
    lazy val port = s.get(serverPort).getOrElse(5001)
    lazy val host = s.get(serverHost).getOrElse("127.0.0.1")
    lazy val auth: Set[ServerAuthentication] =
      s.get(serverAuthentication).getOrElse(Set(ServerAuthentication.Token))
    lazy val connectionType = s.get(serverConnectionType).getOrElse(ConnectionType.Tcp)
    lazy val handlers = s.get(fullServerHandlers).getOrElse(Nil)
    lazy val win32Level = s.get(windowsServerSecurityLevel).getOrElse(2)
    lazy val useJni = s.get(serverUseJni).getOrElse(false)
    lazy val enableBsp = s.get(bspEnabled).getOrElse(true)
    lazy val portfile = s.baseDir / "project" / "target" / "active.json"

    def onIncomingSocket(socket: Socket, instance: ServerInstance): Unit = {
      val name = newNetworkName
      Terminal.consoleLog(s"new client connected: $name")
      val channel =
        new NetworkChannel(
          name,
          socket,
          auth,
          instance,
          handlers,
          mkAskUser(name),
        )
      subscribe(channel)
    }
    if (server.isEmpty && firstInstance.get) {
      val h = Hash.halfHashString(IO.toURI(portfile).toString)
      val serverDir =
        sys.env get "SBT_GLOBAL_SERVER_DIR" map file getOrElse BuildPaths.getGlobalBase(
          s
        ) / "server"
      val tokenfile = serverDir / h / "token.json"
      val socketfile = serverDir / h / "sock"
      val pipeName = "sbt-server-" + h
      val procDir = SysProp.globalLocalCache / "proc"
      val connection = ServerConnection(
        connectionType,
        host,
        port,
        auth,
        portfile,
        tokenfile,
        socketfile,
        pipeName,
        s.configuration,
        win32Level,
        useJni,
        enableBsp,
        Some(procDir),
      )
      val serverInstance = Server.start(connection, onIncomingSocket, s.log)
      // don't throw exception when it times out
      val d = "10s"
      Try(Await.ready(serverInstance.ready, Duration(d)))
      serverInstance.ready.value match {
        case Some(Success(())) =>
          // remember to shutdown only when the server comes up
          server = Some(serverInstance)
          s.log.info("started sbt server")
        case Some(Failure(_: AlreadyRunningException)) =>
          s.log.warn(
            "sbt server could not start because there's another instance of sbt running on this build."
          )
          s.log.warn("Running multiple instances is unsupported")
          server = None
          firstInstance.set(false)
        case Some(Failure(e)) =>
          s.log.error(e.toString)
          server = None
        case None =>
          s.log.warn(s"sbt server could not start in $d")
          server = None
          firstInstance.set(false)
      }
      Terminal.setBootStreams(null, null)

      if (s.get(BasicKeys.detachStdio).getOrElse(false)) {
        Terminal.close()
      }

      s.get(Keys.bootServerSocket).foreach(_.close())
    }
    if (server.isEmpty && !monitoringActiveJson.get) {
      s.get(sbt.nio.Keys.globalFileTreeRepository) match {
        case Some(r) =>
          r.register(sbt.nio.file.Glob(portfile)) match {
            case Right(o) =>
              o.addObserver { event =>
                if (!event.exists) {
                  firstInstance.set(true)
                  monitoringActiveJson.set(false)
                  // FailureWall is effectively a no-op command that will
                  // cause shell to re-run which should start the server
                  commandQueue.add(Exec(BasicCommandStrings.FailureWall, None))
                  o.close()
                }
              }
              monitoringActiveJson.set(true)
            case _ =>
          }
        case _ =>
      }
    }
    s.remove(Keys.bootServerSocket)
  }

  def shutdown(): Unit = {
    fastTrackThread.close()
    channels foreach (_.shutdown(true))
    // interrupt and kill the thread
    server.foreach(_.shutdown())
    server = None
    EvaluateTask.onShutdown()
  }

  /**
   * Notify other sbt servers to drop if idle when a client disconnects.
   * This helps reduce the number of idle servers left running.
   */
  private def notifyOtherServersOnExit(): Unit = {
    val procDir = SysProp.globalLocalCache / "proc"
    if (!procDir.exists) return

    val currentPid = {
      import java.lang.management.ManagementFactory
      ManagementFactory.getRuntimeMXBean.getName.split("@").head
    }
    val procFiles = procDir.listFiles().toList.filter { f =>
      f.getName.endsWith(".json") && !f.getName.startsWith(currentPid)
    }

    procFiles.foreach { procFile =>
      val pid = procFile.getName.stripSuffix(".json")
      if (!isProcessAlive(pid)) {
        // Process is dead, remove stale proc file
        Try(IO.delete(procFile))
      } else {
        Try {
          val content = IO.read(procFile)
          import sbt.internal.server.Server.JsonProtocol.given
          val portFile = Converter.fromJson[PortFile](Parser.parseUnsafe(content)).get
          sendDropIfIdle(portFile)
        }.recover { case NonFatal(_) =>
        // Failed to parse or send, but process is alive - don't delete
        }
      }
    }
  }

  /**
   * Check if a process with the given PID is still running.
   */
  private def isProcessAlive(pid: String): Boolean = {
    Try {
      val command =
        if (scala.util.Properties.isWin) java.util.Arrays.asList("tasklist", "/FI", s"PID eq $pid")
        else java.util.Arrays.asList("kill", "-0", pid)
      val pb = new ProcessBuilder(command)
      pb.redirectErrorStream(true)
      val process = pb.start()
      val exitCode = process.waitFor()
      if (scala.util.Properties.isWin) {
        // On Windows, tasklist returns 0 even if process not found, check output
        val output = scala.io.Source.fromInputStream(process.getInputStream).mkString
        output.contains(pid)
      } else {
        // On Unix, kill -0 returns 0 if process exists
        exitCode == 0
      }
    }.getOrElse(false)
  }

  /**
   * Send dropIfIdle notification to a server.
   */
  private def sendDropIfIdle(portFile: PortFile): Unit = {
    import sbt.internal.protocol.codec.JsonRPCProtocol.given

    Try {
      // Parse the URI to determine connection type
      val uri = portFile.uri
      val socket: Socket =
        if (uri.startsWith("local://")) {
          // Unix domain socket
          val socketPath = uri.stripPrefix("local://")
          new org.scalasbt.ipcsocket.UnixDomainSocket(socketPath, false)
        } else if (uri.startsWith("local:")) {
          // Windows named pipe
          val pipeName = uri.stripPrefix("local:")
          new org.scalasbt.ipcsocket.Win32NamedPipeSocket(pipeName, false)
        } else if (uri.startsWith("tcp://")) {
          // TCP socket
          val hostPort = uri.stripPrefix("tcp://").split(":")
          new Socket(hostPort(0), hostPort(1).toInt)
        } else {
          throw new IOException(s"Unknown server URI format: $uri")
        }

      try {
        socket.setSoTimeout(5000) // 5 second timeout
        val notification = JsonRpcNotificationMessage("2.0", dropIfIdle, None)
        val json = Converter.toJson(notification).get
        val body = CompactPrinter(json)
        val bytes = body.getBytes("UTF-8")
        val message =
          s"Content-Length: ${bytes.length}\r\nContent-Type: application/vscode-jsonrpc; charset=utf-8\r\n\r\n$body"
        socket.getOutputStream.write(message.getBytes("UTF-8"))
        socket.getOutputStream.flush()
      } finally {
        socket.close()
      }
    }.recover { case NonFatal(_) =>
    // Connection failed, but we don't delete proc file here
    // The proc file will be cleaned up when the process dies
    }
  }

  // This is an interface to directly respond events.
  private[sbt] def respondError(
      code: Long,
      message: String,
      execId: Option[String],
      source: Option[CommandSource]
  ): Unit = {
    respondError(JsonRpcResponseError(code, message), execId, source)
  }

  private[sbt] def respondError(
      err: JsonRpcResponseError,
      execId: Option[String],
      source: Option[CommandSource]
  ): Unit = {
    for {
      source <- source.map(_.channelName)
      channel <- channels.collectFirst {
        // broadcast to the source channel only
        case c: NetworkChannel if c.name == source => c
      }
    } tryTo(_.respondError(err, execId))(channel)
  }

  // This is an interface to directly respond events.
  private[sbt] def respondEvent[A: JsonFormat](
      event: A,
      execId: Option[String],
      source: Option[CommandSource]
  ): Unit = {
    for {
      source <- source.map(_.channelName)
      channel <- channels.collectFirst {
        // broadcast to the source channel only
        case c: NetworkChannel if c.name == source => c
      }
    } tryTo(_.respondResult(event, execId))(channel)
  }

  // This is an interface to directly notify events.
  private[sbt] def notifyEvent[A: JsonFormat](method: String, params: A): Unit = {
    channels.foreach {
      case c: NetworkChannel => tryTo(_.notifyEvent(method, params))(c)
      case _                 =>
    }
  }

  private def tryTo(f: NetworkChannel => Unit)(
      channel: NetworkChannel
  ): Unit =
    try f(channel)
    catch { case _: IOException => removeChannel(channel) }

  def respondStatus(event: ExecStatusEvent): Unit = {
    import sbt.protocol.codec.JsonProtocol.given
    for {
      source <- event.channelName
      channel <- channels.collectFirst {
        case c: NetworkChannel if c.name == source => c
      }
    } {
      if (event.execId.isEmpty) {
        tryTo(_.notifyEvent(event))(channel)
      } else {
        event.exitCode match {
          case None | Some(0) =>
            tryTo(_.respondResult(event, event.execId))(channel)
          case Some(code) =>
            tryTo(_.respondError(code, event.message.getOrElse(""), event.execId))(channel)
        }
      }
    }
  }

  private[sbt] def setExec(exec: Option[Exec]): Unit =
    currentExecRef.set(exec.orNull)
    // Update last activity time when a command starts executing
    exec.foreach(_ => lastActivityTime.set(System.currentTimeMillis()))

  /**
   * Returns the number of seconds the server has been idle.
   * Idle means no command has been executed.
   */
  private[sbt] def idleSeconds: Long =
    (System.currentTimeMillis() - lastActivityTime.get()) / 1000

  /**
   * Threshold in seconds for dropIfIdle to trigger shutdown.
   * Default is 600 seconds (10 minutes).
   */
  private def dropIfIdleThresholdSeconds: Long = SysProp.secondaryIdleTimeoutSec

  /**
   * Handle dropIfIdle request from another server.
   * If this server has been idle for more than the threshold and has no connected clients, shut it down.
   */
  private[sbt] def handleDropIfIdle(): Boolean =
    if idleSeconds >= dropIfIdleThresholdSeconds && channels.isEmpty then
      lastState.get match
        case s: State =>
          s.log.info(s"Received dropIfIdle request, idle for $idleSeconds seconds, shutting down")
        case null => // State not yet initialized
      commandQueue.add(Exec(TerminateAction, None))
      true
    else false

  def prompt(event: ConsolePromptEvent): Unit =
    currentExecRef.set(null)
    channels.foreach {
      case c if ContinuousCommands.isInWatch(lastState.get, c) =>
      case c =>
        if c.isPaused then ()
        else c.prompt(event)
    }
  def unprompt(event: ConsoleUnpromptEvent): Unit = channels.foreach(_.unprompt(event))

  def logMessage(event: LogEvent): Unit = {
    channels.foreach {
      case c: NetworkChannel => tryTo(_.notifyEvent(event))(c)
      case _                 =>
    }
  }

  def notifyStatus(event: ExecStatusEvent): Unit = {
    for {
      source <- event.channelName
      channel <- channels.collectFirst {
        case c: NetworkChannel if c.name == source => c
      }
    } tryTo(_.notifyEvent(event))(channel)
  }

  private[sbt] def killChannel(channel: String): Unit = {
    channels.find(_.name == channel).foreach(_.shutdown(false))
  }
  private[sbt] def updateProgress(pe: ProgressEvent): Unit = {
    val newPE = currentExec match {
      case Some(e) if !e.commandLine.startsWith(networkExecPrefix) =>
        pe.withCommand(currentExec.map(_.commandLine))
          .withExecId(currentExec.flatMap(_.execId))
          .withChannelName(currentExec.flatMap(_.source.map(_.channelName)))
      case _ => pe
    }
    channels.foreach(c => ProgressState.updateProgressState(newPE, c.terminal))
  }

  /**
   * When a reboot is initiated by a network client, we need to communicate
   * to it which
   *
   * @param state
   */
  private[sbt] def reboot(state: State): Unit = state.source match {
    case Some(s) if s.channelName.startsWith("network") =>
      channels.foreach {
        case nc: NetworkChannel if nc.name == s.channelName =>
          val remainingCommands =
            state.remainingCommands
              .takeWhile(!_.commandLine.startsWith(CompleteExec))
              .map(_.commandLine)
              .filterNot(_.startsWith("sbtReboot"))
              .mkString(";")
          val execId = state.remainingCommands.collectFirst {
            case e if e.commandLine.startsWith(CompleteExec) =>
              e.commandLine.split(CompleteExec).last.trim
          }
          nc.shutdown(true, execId.map(_ -> remainingCommands))
        case nc: NetworkChannel => nc.shutdown(true, Some(("", "")))
        case _                  =>
      }
    case _ =>
      channels.foreach {
        case nc: NetworkChannel => nc.shutdown(true, Some(("", "")))
        case c                  => c.shutdown(false)
      }
  }

  private[sbt] def shutdown(name: String): Unit = {
    Option(currentExecRef.get).foreach(cancel)
    commandQueue.clear()
    val exit = Exec(Shutdown, Some(Exec.newExecId), Some(CommandSource(name)))
    commandQueue.add(exit)
    ()
  }
  private def cancel(e: Exec): Unit = {
    if (e.commandLine.startsWith("console")) {
      val terminal = Terminal.get
      terminal.write(13, 13, 13, 4)
      terminal.printStream.println("\nconsole session killed by remote sbt client")
    } else {
      Util.ignoreResult(NetworkChannel.cancel(e.execId, e.execId.getOrElse("0"), force = true))
    }
  }

  private class FastTrackThread
      extends Thread("sbt-command-exchange-fastTrack")
      with AutoCloseable {
    setDaemon(true)
    start()
    private val isStopped = new AtomicBoolean(false)
    override def run(): Unit = {
      def exit(mt: FastTrackTask): Unit = {
        mt.channel.shutdown(false)
        if (mt.channel.name.contains("console")) shutdown(mt.channel.name)
      }
      @tailrec def impl(): Unit = {
        fastTrackChannelQueue.take match {
          case null =>
          case mt: FastTrackTask =>
            mt.task match {
              case `attach` | "" => mt.channel.prompt(ConsolePromptEvent(lastState.get))
              case `Cancel` =>
                Option(currentExecRef.get).foreach(cancel)
                mt.channel.prompt(ConsolePromptEvent(lastState.get))
              case t if t.startsWith(ContinuousCommands.stopWatch) =>
                mt.channel match {
                  case c: NetworkChannel if !c.isInteractive => exit(mt)
                  case _                                     =>
                }
                commandQueue.add(Exec(t, None, None))
              case `TerminateAction` => exit(mt)
              case `Shutdown` =>
                val console = Terminal.console
                val needNewLine = console.prompt.isInstanceOf[Prompt.AskUser]
                console.setPrompt(Prompt.Batch)
                if (needNewLine) console.printStream.println()
                channels.find(_.name == mt.channel.name) match {
                  case Some(c: NetworkChannel) => c.shutdown(false)
                  case _                       =>
                }
                shutdown(mt.channel.name)
              case _ =>
            }
        }
        if (!isStopped.get) impl()
      }
      try impl()
      catch { case _: InterruptedException => }
    }
    override def close(): Unit = if (isStopped.compareAndSet(false, true)) {
      interrupt()
    }
  }
  private[sbt] def channelForName(channelName: String): Option[CommandChannel] =
    channels.find(_.name == channelName)
  private val fastTrackThread = new FastTrackThread
}

object CommandExchange:
  /**
   * ServerHandler for dropIfIdle notifications.
   * When a server exits, it sends dropIfIdle to other servers to reduce idle server count.
   */
  val idleHandler: ServerHandler = ServerHandler: callback =>
    ServerIntent(
      onRequest = PartialFunction.empty,
      onResponse = PartialFunction.empty,
      onNotification = {
        case n if n.method == dropIfIdle =>
          StandardMain.exchange.handleDropIfIdle()
          ()
      }
    )
