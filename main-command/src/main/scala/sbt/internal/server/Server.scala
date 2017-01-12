/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.net.{ SocketTimeoutException, InetAddress, ServerSocket, Socket }
import java.util.concurrent.atomic.AtomicBoolean
import sbt.util.Logger

private[sbt] sealed trait ServerInstance {
  def shutdown(): Unit
}

private[sbt] object Server {
  def start(host: String, port: Int, onIncomingSocket: Socket => Unit,
    /*onIncommingCommand: CommandMessage => Unit,*/ log: Logger): ServerInstance =
    new ServerInstance {

      // val lock = new AnyRef {}
      // val clients: mutable.ListBuffer[ClientConnection] = mutable.ListBuffer.empty
      val running = new AtomicBoolean(true)

      val serverThread = new Thread("sbt-socket-server") {

        override def run(): Unit = {
          val serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host))
          serverSocket.setSoTimeout(5000)

          log.info(s"sbt server started at $host:$port")
          while (running.get()) {
            try {
              val socket = serverSocket.accept()
              onIncomingSocket(socket)
            } catch {
              case _: SocketTimeoutException => // its ok
            }

          }
        }
      }
      serverThread.start()

      override def shutdown(): Unit = {
        log.info("shutting down server")
        running.set(false)
      }
    }

}
