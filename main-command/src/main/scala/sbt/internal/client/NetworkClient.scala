/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package client

import java.net.{ URI, Socket, InetAddress, SocketException }
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal
import sbt.protocol._
import sbt.internal.util.{ JLine, StringEvent, ConsoleAppender }
import sbt.util.Level

class NetworkClient(arguments: List[String]) { self =>
  private val channelName = new AtomicReference("_")
  private val status = new AtomicReference("Ready")
  private val lock: AnyRef = new AnyRef {}
  private val running = new AtomicBoolean(true)
  private val pendingExecIds = ListBuffer.empty[String]

  private val console = ConsoleAppender("thin1")

  def usageError = sys.error("Expecting: sbt client 127.0.0.1:port")
  val connection = init()
  start()

  def init(): ServerConnection = {
    val u = arguments match {
      case List(x) =>
        if (x contains "://") new URI(x)
        else new URI("tcp://" + x)
      case _ => usageError
    }
    val host = Option(u.getHost) match {
      case None    => usageError
      case Some(x) => x
    }
    val port = Option(u.getPort) match {
      case None               => usageError
      case Some(x) if x == -1 => usageError
      case Some(x)            => x
    }
    println(s"client on port $port")
    val socket = new Socket(InetAddress.getByName(host), port)
    new ServerConnection(socket) {
      override def onEvent(event: EventMessage): Unit = self.onEvent(event)
      override def onLogEntry(event: StringEvent): Unit = self.onLogEntry(event)
      override def onShutdown(): Unit = {
        running.set(false)
      }
    }
  }

  def onLogEntry(event: StringEvent): Unit = {
    val level = event.level match {
      case "debug" => Level.Debug
      case "info"  => Level.Info
      case "warn"  => Level.Warn
      case "error" => Level.Error
    }
    console.appendLog(level, event.message)
  }

  def onEvent(event: EventMessage): Unit =
    event match {
      case e: ChannelAcceptedEvent =>
        channelName.set(e.channelName)
        println(event)
      case e: ExecStatusEvent =>
        status.set(e.status)
        // println(event)
        e.execId foreach { execId =>
          if (e.status == "Done" && (pendingExecIds contains execId)) {
            lock.synchronized {
              pendingExecIds -= execId
            }
          }
        }
      case e => println(e.toString)
    }

  def start(): Unit = {
    val reader = JLine.simple(None, JLine.HandleCONT, injectThreadSleep = true)
    while (running.get) {
      reader.readLine("> ", None) match {
        case Some("exit") =>
          running.set(false)
        case Some(s) =>
          val execId = UUID.randomUUID.toString
          publishCommand(ExecCommand(s, execId))
          lock.synchronized {
            pendingExecIds += execId
          }
          while (pendingExecIds contains execId) {
            Thread.sleep(100)
          }
        case _ => //
      }
    }
  }

  def publishCommand(command: CommandMessage): Unit = {
    val bytes = Serialization.serializeCommand(command)
    try {
      connection.publish(bytes)
    } catch {
      case _: SocketException =>
      // log.debug(e.getMessage)
      // toDel += client
    }
    lock.synchronized {
      status.set("Processing")
    }
  }
}

object NetworkClient {
  def run(arguments: List[String]): Unit =
    try {
      new NetworkClient(arguments)
    } catch {
      case NonFatal(e) => println(e.getMessage)
    }
}
