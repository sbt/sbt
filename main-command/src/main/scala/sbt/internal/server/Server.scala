/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.io.File
import java.net.{ SocketTimeoutException, InetAddress, ServerSocket, Socket }
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ Future, Promise }
import scala.util.{ Try, Success, Failure }
import sbt.internal.util.ErrorHandling
import sbt.internal.protocol.PortFile
import sbt.util.Logger
import sbt.io.IO
import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter }
import sbt.internal.protocol.codec._

private[sbt] sealed trait ServerInstance {
  def shutdown(): Unit
  def ready: Future[Unit]
}

private[sbt] object Server {
  sealed trait JsonProtocol
      extends sjsonnew.BasicJsonProtocol
      with PortFileFormats
      with TokenFileFormats
  object JsonProtocol extends JsonProtocol

  def start(host: String,
            port: Int,
            onIncomingSocket: Socket => Unit,
            portfile: File,
            tokenfile: File,
            log: Logger): ServerInstance =
    new ServerInstance {
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
              writePortfile()
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
        if (portfile.exists) {
          IO.delete(portfile)
        }
        if (tokenfile.exists) {
          IO.delete(tokenfile)
        }
        running.set(false)
      }

      // This file exists through the lifetime of the server.
      def writePortfile(): Unit = {
        import JsonProtocol._
        val p = PortFile(s"tcp://$host:$port", None)
        val json = Converter.toJson(p).get
        IO.write(portfile, CompactPrinter(json))
      }
    }
}
