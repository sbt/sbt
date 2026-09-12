/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

package internal

import java.io.{ File, FileNotFoundException, IOException }
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
import sbt.internal.nio.FileTreeRepository
import sbt.nio.file.FileAttributes
import sbt.protocol.Serialization.attach
import sbt.protocol.{ ExecStatusEvent, LogEvent }
import sbt.util.Logger
import sjsonnew.JsonFormat

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.{ Failure, Success, Try }

/**
 * The command exchange merges multiple command channels (e.g. network and console),
 * and acts as the central multiplexing point.
 * Instead of blocking on JLine.readLine, the server command will block on
 * this exchange, which could serve command request from either of the channel.
 */
private[sbt] final class CommandExchange:
  private val autoStartServerSysProp =
    sys.props get "sbt.server.autostart" forall (_.toLowerCase == "true")
  private var server: Option[ServerInstance] = None
  private val firstInstance: AtomicBoolean = new AtomicBoolean(true)
  private val watchedRepository = new AtomicReference[AnyRef]
  private val portfileWatch = AtomicCloseable[AutoCloseable]()
  private val commandQueue: LinkedBlockingQueue[Exec] = new LinkedBlockingQueue[Exec]
  private val channelBuffer: ListBuffer[CommandChannel] = new ListBuffer()
  private val channelBufferLock = new AnyRef {}
  private val fastTrackChannelQueue = new LinkedBlockingQueue[FastTrackTask]
  private val nextChannelId: AtomicInteger = new AtomicInteger(0)
  private val lastState = new AtomicReference[State]
  private val currentExecRef = new AtomicReference[Exec]
  private val lastActivityTime = new AtomicLong(System.currentTimeMillis)
  private val shuttingDown = new AtomicBoolean(false)
  @volatile private var procFile: Option[File] = None
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
  ): Exec =
    val idleDeadline = state.flatMap { s =>
      lastState.set(s)
      s.get(BasicKeys.serverIdleTimeout) match
        case Some(Some(d)) => Some(d.fromNow)
        case _             => None
    }
    @tailrec def impl(gcDeadline: Option[Deadline], idleDeadline: Option[Deadline]): Exec =
      state.foreach(s => prompt(ConsolePromptEvent(s)))
      def poll: Option[Exec] =
        val deadline = gcDeadline.toSeq ++ idleDeadline match
          case s @ Seq(_, _) => Some(s.min)
          case s             => s.headOption
        try
          Option(deadline match
            case Some(d: Deadline) =>
              commandQueue.poll(d.timeLeft.toMillis + 1, TimeUnit.MILLISECONDS) match
                case null if idleDeadline.fold(false)(_.isOverdue()) =>
                  state.foreach { s =>
                    s.get(BasicKeys.serverIdleTimeout) match
                      case Some(Some(d)) => s.log.info(s"sbt idle timeout of $d expired")
                      case _             =>
                  }
                  Exec(TerminateAction, Some(CommandSource(ConsoleChannel.defaultName)))
                case x => x
            case _ => commandQueue.take)
        catch case _: InterruptedException => None
      poll match
        case Some(exec) if exec.source.fold(true)(s => channels.exists(_.name == s.channelName)) =>
          exec.commandLine match
            case `TerminateAction`
                if exec.source.fold(false)(_.channelName.startsWith("network")) =>
              channels.collectFirst {
                case c: NetworkChannel if exec.source.fold(false)(_.channelName == c.name) => c
              } match
                case Some(c) if c.isAttached =>
                  c.shutdown(false)
                  impl(gcDeadline, idleDeadline)
                case _ => exec
            case _ => exec
        case Some(e) => e
        case None    =>
          val newDeadline = if gcDeadline.fold(false)(_.isOverdue()) then
            GCUtil.forceGcWithInterval(interval, logger)
            None
          else gcDeadline
          impl(newDeadline, idleDeadline)
    end impl
    // Do not manually run GC until the user has been idling for at least the min gc interval.
    impl(
      interval match
        case d: FiniteDuration => Some(d.fromNow)
        case _                 => None
      ,
      idleDeadline
    )
  end blockUntilNextExec

  private def addConsoleChannel(): Unit =
    if Terminal.startedByRemoteClient then ()
    else
      val name = ConsoleChannel.defaultName
      subscribe(new ConsoleChannel(name, mkAskUser(name)))

  def run(s: State): State = run(s, s.get(autoStartServer).getOrElse(true))
  def run(s: State, autoStart: Boolean): State =
    val startedByRemote = Terminal.startedByRemoteClient
    if autoStartServerSysProp && (autoStart || startedByRemote) then runServer(s)
    else s
  private[sbt] def setState(s: State): Unit = lastState.set(s)

  private def newNetworkName: String = s"network-${nextChannelId.incrementAndGet()}"

  private[sbt] def removeChannel(c: CommandChannel): Unit =
    val wasInitialized = c match
      case nc: NetworkChannel => nc.isInitialized
      case _                  => false
    channelBufferLock.synchronized {
      Util.ignoreResult(channelBuffer -= c)
    }
    def isFromChannel(e: Exec): Boolean = e.source.exists(_.channelName == c.name)
    commandQueue.removeIf { e => isFromChannel(e) && e.commandLine != Shutdown }
    currentExec.foreach { e => if isFromChannel(e) then doCancel(e, force = false) }
    try commandQueue.put(Exec(s"${ContinuousCommands.stopWatch} ${c.name}", None))
    catch
      case _: InterruptedException =>
    // Notify other servers to drop if idle when a real client disconnects
    if wasInitialized && !isShuttingDown then notifyOtherServers()

  private def mkAskUser(
      name: String,
  ): (State, CommandChannel) => UITask = (state, channel) =>
    ContinuousCommands
      .watchUITaskFor(state, channel)
      .getOrElse(new UITask.AskUserTask(state, channel))

  private[sbt] def currentExec = Option(currentExecRef.get)

  /**
   * Check if a server instance is running already, and start one if it isn't.
   */
  private[sbt] def runServer(s: State): State =
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

    def onIncomingSocket(socket: AtomicReference[Socket], instance: ServerInstance): Unit =
      val name = newNetworkName
      Terminal.consoleLog(s"new client connected: $name")
      val channel =
        new NetworkChannel(
          name,
          socket.get,
          auth,
          instance,
          handlers,
          mkAskUser(name),
        )
      subscribe(channel)
      AtomicCloseable.release(socket) // i took over
    if server.isEmpty && firstInstance.get then
      val h = Hash.halfHashString(IO.toURI(portfile).toString)
      val serverDir =
        sys.env get "SBT_GLOBAL_SERVER_DIR" map file getOrElse BuildPaths.getGlobalBase(
          s
        ) / "server"
      val tokenfile = serverDir / h / "token.json"
      val socketfile = serverDir / h / "sock"
      val pipeName = "sbt-server-" + h
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
      )
      val serverInstance = Server.start(connection, onIncomingSocket, s.log)
      // don't throw exception when it times out
      val d = "10s"
      Try(Await.ready(serverInstance.ready, Duration(d)))
      serverInstance.ready.value match
        case Some(Success(())) =>
          // remember to shutdown only when the server comes up
          server = Some(serverInstance)
          s.log.debug("started sbt server")
          // register this server in the shared proc directory
          try
            val procDir = SysProp.globalLocalCache / "proc"
            IO.createDirectory(procDir)
            val pid = ProcessHandle.current().pid()
            val pf = procDir / s"$pid.json"
            IO.copyFile(portfile, pf)
            procFile = Some(pf)
          catch
            case scala.util.control.NonFatal(_) =>
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
      end match
      Terminal.setBootStreams(null, null)

      if s.get(BasicKeys.detachStdio).getOrElse(false) then Terminal.close()

      s.get(Keys.bootServerSocket).foreach(_.close())
    end if
    server.foreach { instance =>
      s.get(sbt.nio.Keys.globalFileTreeRepository).foreach { repo =>
        if watchedRepository.get ne repo then watchPortfile(instance, portfile, repo)
      }
    }
    s.remove(Keys.bootServerSocket)
  end runServer

  /**
   * Registers a watch on the portfile. A project load closes the file tree repository, so the
   * caller registers again on the one that replaced it, and this reads the file once because no
   * event arrived while the old watch was closed.
   */
  private def watchPortfile(
      instance: ServerInstance,
      portfile: File,
      repo: FileTreeRepository[FileAttributes]
  ): Unit =
    def check(): Unit = Server.serverIdOf(portfile) match
      case Failure(_: FileNotFoundException) =>
        Util.ignoreTry(instance.writePortfileIfAbsent()) // a throw here would end the watch
      case Success(id) if !id.contains(instance.serverId) =>
        exitServer("another sbt server took over this build")
      case _ =>
    if replaceWatch(portfile, repo, portfileWatch)(_.addObserver(_ => check())) then
      watchedRepository.set(repo)
    check()

  private def replaceWatch[A](
      portfile: File,
      repo: nio.Registerable[A],
      watch: AtomicCloseable[AutoCloseable]
  )(setUp: nio.Observable[A] => Unit): Boolean =
    watch.close()
    // a repository that a failed load left closed throws instead of returning a Left
    Try(repo.register(sbt.nio.file.Glob(portfile))).flatMap(_.toTry) match
      case Success(o) => setUp(o); watch.set(o); true
      case Failure(e) =>
        Terminal.consoleLog(s"sbt server cannot watch $portfile: $e")
        false

  private def exitServer(reason: String): Unit =
    Terminal.consoleLog(s"$reason; exiting")
    shutdown(ConsoleChannel.defaultName)

  def shutdown(): Unit =
    shuttingDown.set(true)
    procFile.foreach { pf =>
      try IO.delete(pf)
      catch
        case scala.util.control.NonFatal(_) =>
    }
    procFile = None
    fastTrackThread.close()
    portfileWatch.close()
    channels.foreach(c => Util.ignoreTry(c.shutdown(true)))
    // interrupt and kill the thread
    server.foreach(s => Util.ignoreTry(s.shutdown()))
    server = None
    EvaluateTask.onShutdown()

  // This is an interface to directly respond events.
  private[sbt] def respondError(
      code: Long,
      message: String,
      execId: Option[String],
      source: Option[CommandSource]
  ): Unit =
    respondError(JsonRpcResponseError(code, message), execId, source)

  private[sbt] def respondError(
      err: JsonRpcResponseError,
      execId: Option[String],
      source: Option[CommandSource]
  ): Unit =
    for
      source <- source.map(_.channelName)
      channel <- channels.collectFirst {
        // broadcast to the source channel only
        case c: NetworkChannel if c.name == source => c
      }
    do tryTo(_.respondError(err, execId))(channel)

  // This is an interface to directly respond events.
  private[sbt] def respondEvent[A: JsonFormat](
      event: A,
      execId: Option[String],
      source: Option[CommandSource]
  ): Unit =
    for
      source <- source.map(_.channelName)
      channel <- channels.collectFirst {
        // broadcast to the source channel only
        case c: NetworkChannel if c.name == source => c
      }
    do tryTo(_.respondResult(event, execId))(channel)

  // This is an interface to directly notify events.
  private[sbt] def notifyEvent[A: JsonFormat](method: String, params: A): Unit =
    channels.foreach:
      case c: NetworkChannel if c.subscribeToAll || isChannelOwner(c) =>
        tryTo(_.notifyEvent(method, params))(c)
      case _ =>

  private def tryTo(f: NetworkChannel => Unit)(
      channel: NetworkChannel
  ): Unit =
    try f(channel)
    catch case _: IOException => removeChannel(channel)

  def respondStatus(event: ExecStatusEvent): Unit =
    import sbt.protocol.codec.JsonProtocol.given
    for
      source <- event.channelName
      channel <- channels.collectFirst {
        case c: NetworkChannel if c.name == source => c
      }
    do
      if event.execId.isEmpty then tryTo(_.notifyEvent(event))(channel)
      else
        event.exitCode match
          case None | Some(0) =>
            tryTo(_.respondResult(event, event.execId))(channel)
          case Some(code) =>
            tryTo(_.respondError(code, event.message.getOrElse(""), event.execId))(channel)

  private[sbt] def setExec(exec: Option[Exec]): Unit =
    currentExecRef.set(exec.orNull)
    lastActivityTime.set(System.currentTimeMillis)

  private def idleSeconds: Long =
    (System.currentTimeMillis - lastActivityTime.get) / 1000

  def prompt(event: ConsolePromptEvent): Unit =
    currentExecRef.set(null)
    lastActivityTime.set(System.currentTimeMillis)
    channels.foreach {
      case c if ContinuousCommands.isInWatch(lastState.get, c) =>
      case c                                                   =>
        if c.isPaused then ()
        else c.prompt(event)
    }
  def unprompt(event: ConsoleUnpromptEvent): Unit = channels.foreach(_.unprompt(event))

  def logMessage(event: LogEvent): Unit =
    channels.foreach:
      case c: NetworkChannel if c.subscribeToAll || isChannelOwner(c) =>
        tryTo(_.notifyEvent(event))(c)
      case _ =>

  // Route a log event to a specific channel, independent of currentExec.
  // Used for background job output so messages reach the originating client
  // even after the spawning task has completed and currentExec has been cleared.
  private[sbt] def logMessage(channelName: String, event: LogEvent): Unit =
    channels.foreach:
      case c: NetworkChannel if c.subscribeToAll || c.name == channelName =>
        tryTo(_.notifyEvent(event))(c)
      case _ =>

  private def isChannelOwner(c: NetworkChannel): Boolean =
    currentExec.exists(_.source.exists(_.channelName == c.name))

  def notifyStatus(event: ExecStatusEvent): Unit =
    for
      source <- event.channelName
      channel <- channels.collectFirst {
        case c: NetworkChannel if c.name == source => c
      }
    do tryTo(_.notifyEvent(event))(channel)

  private[sbt] def killChannel(channel: String): Unit =
    channels.find(_.name == channel).foreach(_.shutdown(false))
  private[sbt] def updateProgress(pe: ProgressEvent): Unit =
    val newPE = currentExec match
      case Some(e) if !e.commandLine.startsWith(networkExecPrefix) =>
        pe.withCommand(currentExec.map(_.commandLine))
          .withExecId(currentExec.flatMap(_.execId))
          .withChannelName(currentExec.flatMap(_.source.map(_.channelName)))
      case _ => pe
    channels.foreach(c => ProgressState.updateProgressState(newPE, c.terminal))

  /**
   * When a reboot is initiated by a network client, we need to communicate
   * to it which
   *
   * @param state
   */
  private[sbt] def reboot(state: State): Unit = state.source match
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

  private[sbt] def shutdown(name: String): Unit =
    Option(currentExecRef.get).foreach(cancel)
    commandQueue.clear()
    val exit = Exec(Shutdown, Some(Exec.newExecId), Some(CommandSource(name)))
    commandQueue.add(exit)
    ()
  private def cancel(e: Exec): Unit =
    if e.commandLine.startsWith("console") then
      val terminal = Terminal.get
      terminal.write(13, 13, 13, 4)
      terminal.printStream.println("\nconsole session killed by remote sbt client")
    else doCancel(e, force = true)

  private def doCancel(e: Exec, force: Boolean): Unit =
    Util.ignoreResult(NetworkChannel.cancel(e.execId, e.execId.getOrElse("0"), force = force))

  private def isShuttingDown: Boolean = shuttingDown.get

  /** Handle a dropIfIdle notification from another server. */
  private[sbt] def handleDropIfIdle(): Unit =
    val idleSec = idleSeconds
    val threshold = SysProp.secondaryIdleTimeoutSec
    val idle = idleSec >= threshold
    val hasClients = channels.exists {
      case nc: NetworkChannel => nc.isInitialized
      case _                  => false
    }
    if idle && !hasClients then
      exitServer("dropping idle server (requested by another sbt instance)")

  /** Notify other sbt servers to drop if idle. Runs on a daemon thread to avoid blocking. */
  private def notifyOtherServers(): Unit =
    val thread = new Thread("sbt-notify-other-servers"):
      setDaemon(true)

      override def run(): Unit =
        val procDir = SysProp.globalLocalCache / "proc"
        if procDir.exists then
          val myPid = ProcessHandle.current().pid()
          val files = Option(procDir.listFiles).toSeq.flatten
          for f <- files if f.getName.endsWith(".json") do
            val pidStr = f.getName.stripSuffix(".json")
            val pid =
              try pidStr.toLong
              catch case _: NumberFormatException => -1L
            if pid != myPid then
              try
                val (socket, _) = sbt.protocol.ClientSocket.socket(f)
                try
                  val notification = sbt.internal.protocol.JsonRpcNotificationMessage(
                    "2.0",
                    sbt.protocol.Serialization.dropIfIdle,
                    None
                  )
                  val bytes = sbt.protocol.Serialization.serializeNotificationMessage(notification)
                  socket.getOutputStream.write(bytes)
                  socket.getOutputStream.flush()
                finally socket.close()
              catch
                case scala.util.control.NonFatal(_) =>
                  // Server unreachable - clean up stale proc file
                  try IO.delete(f)
                  catch
                    case scala.util.control.NonFatal(_) =>
          end for
        end if
      end run
    thread.start()
  end notifyOtherServers

  private class FastTrackThread extends Thread("sbt-command-exchange-fastTrack") with AutoCloseable:
    setDaemon(true)
    start()
    private val isStopped = new AtomicBoolean(false)
    override def run(): Unit =
      def exit(mt: FastTrackTask): Unit =
        mt.channel.shutdown(false)
        if mt.channel.name.contains("console") then shutdown(mt.channel.name)
      @tailrec def impl(): Unit =
        fastTrackChannelQueue.take match
          case null              =>
          case mt: FastTrackTask =>
            mt.task match
              case `attach` | "" => mt.channel.prompt(ConsolePromptEvent(lastState.get))
              case `Cancel`      =>
                Option(currentExecRef.get).foreach(cancel)
                mt.channel.prompt(ConsolePromptEvent(lastState.get))
              case t if t.startsWith(ContinuousCommands.stopWatch) =>
                mt.channel match
                  case c: NetworkChannel if !c.isInteractive => exit(mt)
                  case _                                     =>
                commandQueue.add(Exec(t, None, None))
              case `TerminateAction` => exit(mt)
              case `Shutdown`        =>
                val console = Terminal.console
                val needNewLine = console.prompt.isInstanceOf[Prompt.AskUser]
                console.setPrompt(Prompt.Batch)
                if needNewLine then console.printStream.println()
                channels.find(_.name == mt.channel.name) match
                  case Some(c: NetworkChannel) => c.shutdown(false)
                  case _                       =>
                shutdown(mt.channel.name)
              case _ =>
        end match
        if !isStopped.get then impl()
      end impl
      try impl()
      catch
        case _: InterruptedException =>
    end run
    override def close(): Unit = if isStopped.compareAndSet(false, true) then interrupt()
  end FastTrackThread
  private[sbt] def channelForName(channelName: String): Option[CommandChannel] =
    channels.find(_.name == channelName)
  private val fastTrackThread = new FastTrackThread
end CommandExchange

private[sbt] object CommandExchange:
  import sbt.protocol.Serialization.dropIfIdle
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
