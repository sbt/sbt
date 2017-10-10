/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import java.net.SocketException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import BasicKeys.{ serverHost, serverPort, serverAuthentication }
import java.net.Socket
import sjsonnew.JsonFormat
import sjsonnew.shaded.scalajson.ast.unsafe._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{ Success, Failure }
import sbt.io.syntax._
import sbt.io.Hash
import sbt.internal.server._
import sbt.internal.util.{ StringEvent, ObjectEvent, ConsoleOut, MainAppender }
import sbt.internal.util.codec.JValueFormats
import sbt.protocol.{ EventMessage, Serialization, ChannelAcceptedEvent }
import sbt.util.{ Level, Logger, LogExchange }

/**
 * The command exchange merges multiple command channels (e.g. network and console),
 * and acts as the central multiplexing point.
 * Instead of blocking on JLine.readLine, the server command will block on
 * this exchange, which could serve command request from either of the channel.
 */
private[sbt] final class CommandExchange {
  private val autoStartServer = sys.props.get("sbt.server.autostart") map {
    _.toLowerCase == "true"
  } getOrElse true
  private val lock = new AnyRef {}
  private var server: Option[ServerInstance] = None
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
      case Some(x) => // do nothing
      case _ =>
        val x = new ConsoleChannel("console0")
        consoleChannel = Some(x)
        subscribe(x)
    }
    if (autoStartServer) runServer(s)
    else s
  }

  private def newChannelName: String = s"channel-${nextChannelId.incrementAndGet()}"

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
    val serverLogLevel: Level.Value = Level.Debug
    def onIncomingSocket(socket: Socket, instance: ServerInstance): Unit = {
      s.log.info(s"new client connected from: ${socket.getPort}")
      val logger: Logger = {
        val loggerName = s"network-${socket.getPort}"
        val log = LogExchange.logger(loggerName, None, None)
        LogExchange.unbindLoggerAppenders(loggerName)
        val appender = MainAppender.defaultScreen(s.globalLogging.console)
        LogExchange.bindLoggerAppenders(loggerName, List(appender -> serverLogLevel))
        log
      }
      val channel =
        new NetworkChannel(newChannelName, socket, Project structure s, auth, instance, logger)
      subscribe(channel)
    }
    server match {
      case Some(x) => // do nothing
      case _ =>
        val portfile = (new File(".")).getAbsoluteFile / "project" / "target" / "active.json"
        val h = Hash.halfHashString(portfile.toURI.toString)
        val tokenfile = BuildPaths.getGlobalBase(s) / "server" / h / "token.json"
        val x = Server.start(host, port, onIncomingSocket, auth, portfile, tokenfile, s.log)
        Await.ready(x.ready, Duration("10s"))
        x.ready.value match {
          case Some(Success(_)) =>
          case Some(Failure(e)) =>
            s.log.error(e.toString)
          case None => // this won't happen because we awaited
        }
        server = Some(x)
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
      case c: ConsoleChannel =>
      // c.publishEvent(event)
      case c: NetworkChannel =>
        try {
          c.notifyEvent(method, params)
        } catch {
          case e: SocketException =>
            toDel += c
        }
    }
    toDel.toList match {
      case Nil => // do nothing
      case xs =>
        lock.synchronized {
          channelBuffer --= xs
        }
    }
  }

  def publishEvent[A: JsonFormat](event: A): Unit = {
    val toDel: ListBuffer[CommandChannel] = ListBuffer.empty
    event match {
      case entry: StringEvent =>
        channels.foreach {
          case c: ConsoleChannel =>
            if (entry.channelName.isEmpty || entry.channelName == Some(c.name)) {
              c.publishEvent(event)
            }
          case c: NetworkChannel =>
            try {
              if (entry.channelName == Some(c.name)) {
                c.publishEvent(event)
              }
            } catch {
              case e: SocketException =>
                toDel += c
            }
        }
      case _ =>
        channels.foreach {
          case c: ConsoleChannel =>
            c.publishEvent(event)
          case c: NetworkChannel =>
            try {
              c.publishEvent(event)
            } catch {
              case e: SocketException =>
                toDel += c
            }
        }
    }
    toDel.toList match {
      case Nil => // do nothing
      case xs =>
        lock.synchronized {
          channelBuffer --= xs
        }
    }
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
    channels.foreach {
      case c: ConsoleChannel =>
        c.publishEvent(json)
      case c: NetworkChannel =>
        try {
          c.publishObjectEvent(event)
        } catch {
          case e: SocketException =>
            toDel += c
        }
    }
    toDel.toList match {
      case Nil => // do nothing
      case xs =>
        lock.synchronized {
          channelBuffer --= xs
        }
    }
  }

  // fanout publishEvent
  def publishEventMessage(event: EventMessage): Unit = {
    val toDel: ListBuffer[CommandChannel] = ListBuffer.empty
    event match {
      // Special treatment for ConsolePromptEvent since it's hand coded without codec.
      case e: ConsolePromptEvent =>
        channels collect {
          case c: ConsoleChannel => c.publishEventMessage(e)
        }
      case e: ConsoleUnpromptEvent =>
        channels collect {
          case c: ConsoleChannel => c.publishEventMessage(e)
        }
      case _ =>
        channels.foreach {
          case c: ConsoleChannel =>
            c.publishEventMessage(event)
          case c: NetworkChannel =>
            try {
              c.publishEventMessage(event)
            } catch {
              case e: SocketException =>
                toDel += c
            }
        }
    }
    toDel.toList match {
      case Nil => // do nothing
      case xs =>
        lock.synchronized {
          channelBuffer --= xs
        }
    }
  }
}
