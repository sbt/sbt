/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.net.{ SocketTimeoutException, InetAddress, ServerSocket }
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import sbt.util.Logger

private[sbt] sealed trait ServerInstance {
  def shutdown(): Unit
  def publish(event: Event): Unit
}

private[sbt] object Server {
  def start(host: String, port: Int, onIncommingCommand: Command => Unit, log: Logger): ServerInstance =
    new ServerInstance {

      val lock = new AnyRef {}
      var clients = Vector[ClientConnection]()
      val running = new AtomicBoolean(true)

      val serverThread = new Thread("sbt-socket-server") {

        override def run(): Unit = {
          val serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host))
          serverSocket.setSoTimeout(5000)

          log.info(s"sbt server started at $host:$port")
          while (running.get()) {
            try {
              val socket = serverSocket.accept()
              log.info(s"new client connected from: ${socket.getPort}")

              val connection = new ClientConnection(socket) {
                override def onCommand(command: Command): Unit = {
                  onIncommingCommand(command)
                }
              }

              lock.synchronized {
                clients = clients :+ connection
              }

            } catch {
              case _: SocketTimeoutException => // its ok
            }

          }
        }
      }
      serverThread.start()

      /** Publish an event to all connected clients */
      def publish(event: Event): Unit = {
        // TODO do not do this on the calling thread
        val bytes = Serialization.serialize(event)
        lock.synchronized {
          clients.foreach(_.publish(bytes))
        }
      }

      override def shutdown(): Unit = {
        log.info("shutting down server")
        running.set(false)
      }
    }

}
