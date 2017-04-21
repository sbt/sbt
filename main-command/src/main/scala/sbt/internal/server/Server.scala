/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.net.{ SocketTimeoutException, InetAddress, ServerSocket, Socket }
import java.util.concurrent.atomic.AtomicBoolean
import sbt.util.Logger
import sbt.internal.util.ErrorHandling
import scala.concurrent.{ Future, Promise }
import scala.util.{ Try, Success, Failure }

private[sbt] sealed trait ServerInstance {
  def shutdown(): Unit
  def ready: Future[Unit]
}

private[sbt] object Server {
  def start(host: String,
            port: Int,
            onIncomingSocket: Socket => Unit,
            /*onIncomingCommand: CommandMessage => Unit,*/ log: Logger): ServerInstance =
    new ServerInstance {

      // val lock = new AnyRef {}
      // val clients: mutable.ListBuffer[ClientConnection] = mutable.ListBuffer.empty
      val running = new AtomicBoolean(false)
      val p: Promise[Unit] = Promise[Unit]()
      val ready: Future[Unit] = p.future

      val serverThread = new Thread("sbt-socket-server") {
        override def run(): Unit = {
          Try {
            ErrorHandling.translate(s"server failed to start on $host:$port. ") {
              new ServerSocket(port, 50, InetAddress.getByName(host))
            }
          } match {
            case Failure(e) => p.failure(e)
            case Success(serverSocket) =>
              serverSocket.setSoTimeout(5000)
              log.info(s"sbt server started at $host:$port")
              running.set(true)
              p.success(())
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
      }
      serverThread.start()

      override def shutdown(): Unit = {
        log.info("shutting down server")
        running.set(false)
      }
    }

}
