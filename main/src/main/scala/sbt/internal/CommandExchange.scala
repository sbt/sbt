/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

package internal
import java.io.IOException
import java.net.Socket
import java.util.concurrent.{ ConcurrentLinkedQueue, LinkedBlockingQueue, TimeUnit }
import java.util.concurrent.atomic._

import sbt.BasicKeys._
import sbt.nio.Watch.NullLogger
import sbt.internal.protocol.JsonRpcResponseError
import sbt.internal.server._
import sbt.internal.util.{ ConsoleOut, MainAppender, ProgressEvent, ProgressState, Terminal }
import sbt.io.syntax._
import sbt.io.{ Hash, IO }
import sbt.protocol.{ ExecStatusEvent, LogEvent }
import sbt.util.{ Level, LogExchange, Logger }
import sjsonnew.JsonFormat

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

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
  private var consoleChannel: Option[ConsoleChannel] = None
  private val commandQueue: ConcurrentLinkedQueue[Exec] = new ConcurrentLinkedQueue()
  private val channelBuffer: ListBuffer[CommandChannel] = new ListBuffer()
  private val channelBufferLock = new AnyRef {}
  private val commandChannelQueue = new LinkedBlockingQueue[CommandChannel]
  private val nextChannelId: AtomicInteger = new AtomicInteger(0)
  private[this] val activePrompt = new AtomicBoolean(false)
  private[this] val currentExecRef = new AtomicReference[Exec]

  def channels: List[CommandChannel] = channelBuffer.toList
  private[this] def removeChannel(channel: CommandChannel): Unit = {
    channelBufferLock.synchronized {
      channelBuffer -= channel
      ()
    }
  }

  def subscribe(c: CommandChannel): Unit = channelBufferLock.synchronized {
    channelBuffer.append(c)
    c.register(commandChannelQueue)
  }

  def blockUntilNextExec: Exec = blockUntilNextExec(Duration.Inf, NullLogger)
  // periodically move all messages from all the channels
  private[sbt] def blockUntilNextExec(interval: Duration, logger: Logger): Exec = {
    @tailrec def impl(deadline: Option[Deadline]): Exec = {
      @tailrec def slurpMessages(): Unit =
        channels.foldLeft(Option.empty[Exec]) { _ orElse _.poll } match {
          case None => ()
          case Some(x) =>
            commandQueue.add(x)
            slurpMessages()
        }
      commandChannelQueue.poll(1, TimeUnit.SECONDS)
      slurpMessages()
      Option(commandQueue.poll) match {
        case Some(exec) =>
          val needFinish = needToFinishPromptLine()
          if (exec.source.fold(needFinish)(s => needFinish && s.channelName != "console0"))
            ConsoleOut.systemOut.println("")
          exec
        case None =>
          val newDeadline = if (deadline.fold(false)(_.isOverdue())) {
            GCUtil.forceGcWithInterval(interval, logger)
            None
          } else deadline
          impl(newDeadline)
      }
    }
    // Do not manually run GC until the user has been idling for at least the min gc interval.
    impl(interval match {
      case d: FiniteDuration => Some(d.fromNow)
      case _                 => None
    })
  }

  def run(s: State): State = {
    if (consoleChannel.isEmpty) {
      val console0 = new ConsoleChannel("console0")
      consoleChannel = Some(console0)
      subscribe(console0)
    }
    val autoStartServerAttr = s get autoStartServer match {
      case Some(bool) => bool
      case None       => true
    }
    if (autoStartServerSysProp && autoStartServerAttr) runServer(s)
    else s
  }

  private def newNetworkName: String = s"network-${nextChannelId.incrementAndGet()}"

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
    lazy val level = s.get(serverLogLevel).orElse(s.get(logLevel)).getOrElse(Level.Warn)
    lazy val handlers = s.get(fullServerHandlers).getOrElse(Nil)

    def onIncomingSocket(socket: Socket, instance: ServerInstance): Unit = {
      val name = newNetworkName
      if (needToFinishPromptLine()) ConsoleOut.systemOut.println("")
      s.log.info(s"new client connected: $name")
      val logger: Logger = {
        val log = LogExchange.logger(name, None, None)
        LogExchange.unbindLoggerAppenders(name)
        val appender = MainAppender.defaultScreen(s.globalLogging.console)
        LogExchange.bindLoggerAppenders(name, List(appender -> level))
        log
      }
      val channel =
        new NetworkChannel(name, socket, Project structure s, auth, instance, handlers, logger)
      subscribe(channel)
    }
    if (server.isEmpty && firstInstance.get) {
      val portfile = s.baseDir / "project" / "target" / "active.json"
      val h = Hash.halfHashString(IO.toURI(portfile).toString)
      val serverDir =
        sys.env get "SBT_GLOBAL_SERVER_DIR" map file getOrElse BuildPaths.getGlobalBase(s) / "server"
      val tokenfile = serverDir / h / "token.json"
      val socketfile = serverDir / h / "sock"
      val pipeName = "sbt-server-" + h
      val bspConnectionFile = s.baseDir / ".bsp" / "sbt.json"
      val connection = ServerConnection(
        connectionType,
        host,
        port,
        auth,
        portfile,
        tokenfile,
        socketfile,
        pipeName,
        bspConnectionFile,
        s.configuration
      )
      val serverInstance = Server.start(connection, onIncomingSocket, s.log)
      // don't throw exception when it times out
      val d = "10s"
      Try(Await.ready(serverInstance.ready, Duration(d)))
      serverInstance.ready.value match {
        case Some(Success(())) =>
          // remember to shutdown only when the server comes up
          server = Some(serverInstance)
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
    }
    s
  }

  def shutdown(): Unit = {
    channels foreach (_.shutdown())
    // interrupt and kill the thread
    server.foreach(_.shutdown())
    server = None
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
    channels
      .collect { case c: NetworkChannel => c }
      .foreach {
        tryTo(_.notifyEvent(method, params))
      }
  }

  private def tryTo(f: NetworkChannel => Unit)(
      channel: NetworkChannel
  ): Unit =
    try f(channel)
    catch { case _: IOException => removeChannel(channel) }

  def respondStatus(event: ExecStatusEvent): Unit = {
    import sbt.protocol.codec.JsonProtocol._
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

      tryTo(_.respond(event, event.execId))(channel)
    }
  }

  private[sbt] def setExec(exec: Option[Exec]): Unit = currentExecRef.set(exec.orNull)

  def prompt(event: ConsolePromptEvent): Unit = {
    activePrompt.set(Terminal.systemInIsAttached)
    channels
      .collect { case c: ConsoleChannel => c }
      .foreach { _.prompt(event) }
  }

  def logMessage(event: LogEvent): Unit = {
    channels
      .collect { case c: NetworkChannel => c }
      .foreach {
        tryTo(_.notifyEvent(event))
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

  private[this] def needToFinishPromptLine(): Boolean = activePrompt.compareAndSet(true, false)
  private[sbt] def updateProgress(pe: ProgressEvent): Unit = {
    val newPE = currentExec match {
      case Some(e) =>
        pe.withCommand(currentExec.map(_.commandLine))
          .withExecId(currentExec.flatMap(_.execId))
          .withChannelName(currentExec.flatMap(_.source.map(_.channelName)))
      case _ => pe
    }
    ProgressState.updateProgressState(newPE)
  }
}
