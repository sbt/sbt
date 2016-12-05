/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package sbt
package internal
package client

import java.net.{ URI, Socket, InetAddress, SocketException }
import sbt.protocol._
import sbt.internal.util.JLine
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

class NetworkClient(arguments: List[String]) {
  private val status = new AtomicReference("Ready")
  private val lock: AnyRef = new AnyRef {}
  private val running = new AtomicBoolean(true)
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
      override def onEvent(event: EventMessage): Unit =
        event match {
          case e: StatusEvent =>
            lock.synchronized {
              status.set(e.status)
            }
            println(event)
          case e => println(e.toString)
        }
      override def onShutdown(): Unit =
        {
          running.set(false)
        }
    }
  }

  def start(): Unit =
    {
      val reader = JLine.simple(None, JLine.HandleCONT, injectThreadSleep = true)
      while (running.get) {
        reader.readLine("> ", None) match {
          case Some("exit") =>
            running.set(false)
          case Some(s) =>
            publishCommand(ExecCommand(s))
          case _ => //
        }
        while (status.get != "Ready") {
          Thread.sleep(100)
        }
      }
    }

  def publishCommand(command: CommandMessage): Unit =
    {
      val bytes = Serialization.serializeCommand(command)
      try {
        connection.publish(bytes)
      } catch {
        case e: SocketException =>
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
      case e: Exception => println(e.getMessage)
    }
}
