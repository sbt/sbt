/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.net.{ SocketTimeoutException, InetAddress, ServerSocket }
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

private[sbt] sealed trait ServerInstance {
  def shutdown(): Unit
  def publish(event: Event): Unit
}

private[sbt] object Server {
  def start(host: String, port: Int, onIncommingCommand: Command => Unit): ServerInstance =
    new ServerInstance {

      val lock = new AnyRef {}
      var clients = Vector[ClientConnection]()
      val running = new AtomicBoolean(true)

      val serverThread = new Thread("sbt-socket-server") {

        override def run(): Unit = {
          val serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host))
          serverSocket.setSoTimeout(5000)

          println(s"SBT socket server started at $host:$port")
          while (running.get()) {
            try {
              val socket = serverSocket.accept()
              println(s"New client connected from: ${socket.getPort}")

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
        println("Shutting down server")
        running.set(false)
      }
    }

}
