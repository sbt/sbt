/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic._
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import BasicKeys.{
  autoStartServer,
  serverHost,
  serverPort,
  serverAuthentication,
  serverConnectionType,
  serverLogLevel,
  logLevel
}
import java.net.Socket
import sjsonnew.JsonFormat
import sjsonnew.shaded.scalajson.ast.unsafe._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{ Success, Failure, Try }
import sbt.io.syntax._
import sbt.io.{ Hash, IO }
import sbt.internal.server._
import sbt.internal.langserver.{ LogMessageParams, MessageType }
import sbt.internal.util.{ StringEvent, ObjectEvent, MainAppender }
import sbt.internal.util.codec.JValueFormats
import sbt.protocol.{ EventMessage, ExecStatusEvent }
import sbt.util.{ Level, Logger, LogExchange }

/**
 * The command exchange merges multiple command channels (e.g. network and console),
 * and acts as the central multiplexing point.
 * Instead of blocking on JLine.readLine, the server command will block on
 * this exchange, which could serve command request from either of the channel.
 */
private[sbt] final class CommandExchange {
  private val autoStartServerSysProp = sys.props.get("sbt.server.autostart") map {
    _.toLowerCase == "true"
  } getOrElse true
  private val lock = new AnyRef {}
  private var server: Option[ServerInstance] = None
  private val firstInstance: AtomicBoolean = new AtomicBoolean(true)
  private var consoleChannel: Option[ConsoleChannel] = None
  private val commandQueue: ConcurrentLinkedQueue[Exec] = new ConcurrentLinkedQueue()
  private val channelBuffer: ListBuffer[CommandChannel] = new ListBuffer()
  private val nextChannelId: AtomicInteger = new AtomicInteger(0)
  private lazy val jsonFormat = new sjsonnew.BasicJsonProtocol with JValueFormats {}

  def channels: List[CommandChannel] = channelBuffer.toList
  def subscribe(c: CommandChannel): Unit =
    lock.synchronized {
      channelBuffer.append(c)
    }

  // periodically move all messages from all the channels
  @tailrec def blockUntilNextExec: Exec = {
    @tailrec def slurpMessages(): Unit =
      (((None: Option[Exec]) /: channels) { _ orElse _.poll }) match {
        case Some(x) =>
          commandQueue.add(x)
          slurpMessages
        case _ => ()
      }
    slurpMessages()
    Option(commandQueue.poll) match {
      case Some(x) => x
      case _ =>
        Thread.sleep(50)
        blockUntilNextExec
    }
  }

  def run(s: State): State = {
    consoleChannel match {
      case Some(_) => // do nothing
      case _ =>
        val x = new ConsoleChannel("console0")
        consoleChannel = Some(x)
        subscribe(x)
    }
    val autoStartServerAttr = (s get autoStartServer) match {
      case Some(bool) => bool
      case None       => true
    }
    if (autoStartServerSysProp && autoStartServerAttr) runServer(s)
    else s
  }

  private def newNetworkName: String = s"network-${nextChannelId.incrementAndGet()}"

  /**
   * Check if a server instance is running already, and start one if it isn't.
   */
  private[sbt] def runServer(s: State): State = {
    lazy val port = (s get serverPort) match {
      case Some(x) => x
      case None    => 5001
    }
    lazy val host = (s get serverHost) match {
      case Some(x) => x
      case None    => "127.0.0.1"
    }
    lazy val auth: Set[ServerAuthentication] = (s get serverAuthentication) match {
      case Some(xs) => xs
      case None     => Set(ServerAuthentication.Token)
    }
    lazy val connectionType = (s get serverConnectionType) match {
      case Some(x) => x
      case None    => ConnectionType.Tcp
    }
    lazy val level: Level.Value = (s get serverLogLevel)
      .orElse(s get logLevel)
      .getOrElse(Level.Warn)

    def onIncomingSocket(socket: Socket, instance: ServerInstance): Unit = {
      val name = newNetworkName
      s.log.info(s"new client connected: $name")
      val logger: Logger = {
        val log = LogExchange.logger(name, None, None)
        LogExchange.unbindLoggerAppenders(name)
        val appender = MainAppender.defaultScreen(s.globalLogging.console)
        LogExchange.bindLoggerAppenders(name, List(appender -> level))
        log
      }
      val channel =
        new NetworkChannel(name, socket, Project structure s, auth, instance, logger)
      subscribe(channel)
    }
    server match {
      case Some(_)                    => // do nothing
      case None if !firstInstance.get => // there's another server
      case _ =>
        val portfile = (new File(".")).getAbsoluteFile / "project" / "target" / "active.json"
        val h = Hash.halfHashString(IO.toURI(portfile).toString)
        val tokenfile = BuildPaths.getGlobalBase(s) / "server" / h / "token.json"
        val socketfile = BuildPaths.getGlobalBase(s) / "server" / h / "sock"
        val pipeName = "sbt-server-" + h
        val connection =
          ServerConnection(connectionType,
                           host,
                           port,
                           auth,
                           portfile,
                           tokenfile,
                           socketfile,
                           pipeName)
        val x = Server.start(connection, onIncomingSocket, s.log)

        // don't throw exception when it times out
        val d = "10s"
        Try(Await.ready(x.ready, Duration(d)))
        x.ready.value match {
          case Some(Success(_)) =>
            // rememeber to shutdown only when the server comes up
            server = Some(x)
          case Some(Failure(e: AlreadyRunningException)) =>
            s.log.warn(
              "sbt server could not start because there's another instance of sbt running on this build.")
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
    channels foreach { c =>
      c.shutdown()
    }
    // interrupt and kill the thread
    server.foreach(_.shutdown())
    server = None
  }

  // This is an interface to directly notify events.
  private[sbt] def notifyEvent[A: JsonFormat](method: String, params: A): Unit = {
    val toDel: ListBuffer[CommandChannel] = ListBuffer.empty
    channels.foreach {
      case _: ConsoleChannel =>
      // c.publishEvent(event)
      case c: NetworkChannel =>
        try {
          c.notifyEvent(method, params)
        } catch {
          case _: IOException =>
            toDel += c
        }
    }
    toDel.toList match {
      case Nil => // do nothing
      case xs =>
        lock.synchronized {
          channelBuffer --= xs
          ()
        }
    }
  }

  def publishEvent[A: JsonFormat](event: A): Unit = {
    val broadcastStringMessage = true
    val toDel: ListBuffer[CommandChannel] = ListBuffer.empty

    event match {
      case entry: StringEvent =>
        val params = toLogMessageParams(entry)
        channels collect {
          case c: ConsoleChannel =>
            if (broadcastStringMessage) {
              c.publishEvent(event)
            } else {
              if (entry.channelName.isEmpty || entry.channelName == Some(c.name)) {
                c.publishEvent(event)
              }
            }
          case c: NetworkChannel =>
            try {
              // Note that language server's LogMessageParams does not hold the execid,
              // so this is weaker than the StringMessage. We might want to double-send
              // in case we have a better client that can utilize the knowledge.
              import sbt.internal.langserver.codec.JsonProtocol._
              if (broadcastStringMessage) {
                c.langNotify("window/logMessage", params)
              } else {
                if (entry.channelName == Some(c.name)) {
                  c.langNotify("window/logMessage", params)
                }
              }
            } catch {
              case _: IOException =>
                toDel += c
            }
        }
      case _ =>
        channels collect {
          case c: ConsoleChannel =>
            c.publishEvent(event)
          case c: NetworkChannel =>
            try {
              c.publishEvent(event)
            } catch {
              case _: IOException =>
                toDel += c
            }
        }
    }
    toDel.toList match {
      case Nil => // do nothing
      case xs =>
        lock.synchronized {
          channelBuffer --= xs
          ()
        }
    }
  }

  private[sbt] def toLogMessageParams(event: StringEvent): LogMessageParams = {
    LogMessageParams(MessageType.fromLevelString(event.level), event.message)
  }

  /**
   * This publishes object events. The type information has been
   * erased because it went through logging.
   */
  private[sbt] def publishObjectEvent(event: ObjectEvent[_]): Unit = {
    import jsonFormat._
    val toDel: ListBuffer[CommandChannel] = ListBuffer.empty
    def json: JValue = JObject(
      JField("type", JString(event.contentType)),
      (Vector(JField("message", event.json), JField("level", JString(event.level.toString))) ++
        (event.channelName.toVector map { channelName =>
          JField("channelName", JString(channelName))
        }) ++
        (event.execId.toVector map { execId =>
          JField("execId", JString(execId))
        })): _*
    )
    channels collect {
      case c: ConsoleChannel =>
        c.publishEvent(json)
      case c: NetworkChannel =>
        try {
          c.publishObjectEvent(event)
        } catch {
          case _: IOException =>
            toDel += c
        }
    }
    toDel.toList match {
      case Nil => // do nothing
      case xs =>
        lock.synchronized {
          channelBuffer --= xs
          ()
        }
    }
  }

  // fanout publishEvent
  def publishEventMessage(event: EventMessage): Unit = {
    val toDel: ListBuffer[CommandChannel] = ListBuffer.empty
    event match {
      // Special treatment for ConsolePromptEvent since it's hand coded without codec.
      case entry: ConsolePromptEvent =>
        channels collect {
          case c: ConsoleChannel => c.publishEventMessage(entry)
        }
      case entry: ConsoleUnpromptEvent =>
        channels collect {
          case c: ConsoleChannel => c.publishEventMessage(entry)
        }
      case entry: ExecStatusEvent =>
        channels collect {
          case c: ConsoleChannel =>
            if (entry.channelName.isEmpty || entry.channelName == Some(c.name)) {
              c.publishEventMessage(event)
            }
          case c: NetworkChannel =>
            try {
              if (entry.channelName == Some(c.name)) {
                c.publishEventMessage(event)
              }
            } catch {
              case e: IOException =>
                toDel += c
            }
        }
      case _ =>
        channels collect {
          case c: ConsoleChannel =>
            c.publishEventMessage(event)
          case c: NetworkChannel =>
            try {
              c.publishEventMessage(event)
            } catch {
              case _: IOException =>
                toDel += c
            }
        }
    }
    toDel.toList match {
      case Nil => // do nothing
      case xs =>
        lock.synchronized {
          channelBuffer --= xs
          ()
        }
    }
  }
}
