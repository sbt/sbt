/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package server

import java.net.{ SocketTimeoutException, InetAddress, ServerSocket }
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

sealed trait ServerInstance {
  def shutdown(): Unit
  def nextCommand(): Option[Command]
}

object Server {
  def start(host: String, port: Int): ServerInstance =
    new ServerInstance {

      val commandQueue = new ConcurrentLinkedQueue[Command]()

      val lock = new AnyRef {}
      var clients = Vector[ClientConnection]()
      val running = new AtomicBoolean(true)

      val serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host))
      serverSocket.setSoTimeout(5000)

      val serverThread = new Thread("sbt-socket-server") {

        override def run(): Unit = {
          println(s"SBT socket server started at $host:$port")
          while (running.get()) {
            try {
              val socket = serverSocket.accept()
              println(s"New client connected from: ${socket.getPort}")

              val connection = new ClientConnection(socket) {
                override def onCommand(command: Command): Unit = {
                  println(s"onCommand $command")
                  commandQueue.add(command)
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

      /**
       * @return The next queued command if there is one. It will have to be consumed because it is taken off the queue.
       */
      def nextCommand(): Option[Command] = {
        Option(commandQueue.poll())
      }

      override def shutdown(): Unit = {
        println("Shutting down server")
        running.set(false)
      }
    }

}