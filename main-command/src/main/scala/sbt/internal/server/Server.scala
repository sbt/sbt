/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.io.File
import java.net.{ SocketTimeoutException, InetAddress, ServerSocket, Socket }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong }
import java.nio.file.attribute.{ UserPrincipal, AclEntry, AclEntryPermission, AclEntryType }
import scala.concurrent.{ Future, Promise }
import scala.util.{ Try, Success, Failure, Random }
import sbt.internal.util.ErrorHandling
import sbt.internal.protocol.{ PortFile, TokenFile }
import sbt.util.Logger
import sbt.io.IO
import sbt.io.syntax._
import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter }
import sbt.internal.protocol.codec._

private[sbt] sealed trait ServerInstance {
  def shutdown(): Unit
  def ready: Future[Unit]
  def authenticate(challenge: String): Boolean
}

private[sbt] object Server {
  sealed trait JsonProtocol
      extends sjsonnew.BasicJsonProtocol
      with PortFileFormats
      with TokenFileFormats
  object JsonProtocol extends JsonProtocol

  def start(host: String,
            port: Int,
            onIncomingSocket: (Socket, ServerInstance) => Unit,
            auth: Set[ServerAuthentication],
            portfile: File,
            tokenfile: File,
            log: Logger): ServerInstance =
    new ServerInstance { self =>
      val running = new AtomicBoolean(false)
      val p: Promise[Unit] = Promise[Unit]()
      val ready: Future[Unit] = p.future
      val token = new AtomicLong(Random.nextLong)

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
                  onIncomingSocket(socket, self)
                } catch {
                  case _: SocketTimeoutException => // its ok
                }
              }
          }
        }
      }
      serverThread.start()

      override def authenticate(challenge: String): Boolean = {
        try {
          val l = challenge.toLong
          token.compareAndSet(l, Random.nextLong)
        } catch {
          case _: NumberFormatException => false
        }
      }

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

      def writeTokenfile(): Unit = {
        import JsonProtocol._

        val uri = s"tcp://$host:$port"
        val t = TokenFile(uri, token.get.toString)
        val jsonToken = Converter.toJson(t).get

        if (tokenfile.exists) {
          IO.delete(tokenfile)
        }
        IO.touch(tokenfile)
        ownerOnly(tokenfile)
        IO.write(tokenfile, CompactPrinter(jsonToken), IO.utf8, true)
      }

      /** Set the persmission of the file such that the only the owner can read/write it. */
      def ownerOnly(file: File): Unit = {
        def acl(owner: UserPrincipal) = {
          val builder = AclEntry.newBuilder
          builder.setPrincipal(owner)
          builder.setPermissions(AclEntryPermission.values(): _*)
          builder.setType(AclEntryType.ALLOW)
          builder.build
        }
        file match {
          case _ if IO.isPosix =>
            IO.chmod("rw-------", file)
          case _ if IO.hasAclFileAttributeView =>
            val view = file.aclFileAttributeView
            view.setAcl(java.util.Collections.singletonList(acl(view.getOwner)))
          case _ => ()
        }
      }

      // This file exists through the lifetime of the server.
      def writePortfile(): Unit = {
        import JsonProtocol._

        val uri = s"tcp://$host:$port"
        val tokenRef =
          if (auth(ServerAuthentication.Token)) {
            writeTokenfile()
            Some(tokenfile.toURI.toString)
          } else None
        val p = PortFile(uri, tokenRef)
        val json = Converter.toJson(p).get
        IO.write(portfile, CompactPrinter(json))
      }
    }
}
