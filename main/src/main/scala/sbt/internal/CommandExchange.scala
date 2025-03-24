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
    if (autoStartServerSysProp && autoStart) runServer(s)
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
    import sbt.protocol.codec.JsonProtocol.*
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

  private[sbt] def setExec(exec: Option[Exec]): Unit = currentExecRef.set(exec.orNull)

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
