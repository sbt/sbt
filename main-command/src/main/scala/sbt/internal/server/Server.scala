/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.io.{ File, IOException }
import java.net.{ InetAddress, ServerSocket, Socket, SocketException, SocketTimeoutException }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import java.nio.file.attribute.{ AclEntry, AclEntryPermission, AclEntryType, UserPrincipal }
import java.security.SecureRandom
import java.math.BigInteger

import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success, Try }
import sbt.internal.protocol.{ PortFile, TokenFile }
import sbt.util.Logger
import sbt.io.IO
import sbt.io.syntax.*
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }
import sbt.internal.protocol.codec.*
import sbt.internal.util.ErrorHandling
import sbt.internal.util.Util.isWindows
import org.scalasbt.ipcsocket.*
import sbt.internal.bsp.BuildServerConnection
import xsbti.AppConfiguration

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

  def start(
      connection: ServerConnection,
      onIncomingSocket: (Socket, ServerInstance) => Unit,
      log: Logger
  ): ServerInstance =
    new ServerInstance { self =>
      import connection.*
      val running = new AtomicBoolean(false)
      val p: Promise[Unit] = Promise[Unit]()
      val ready: Future[Unit] = p.future
      private val rand = new SecureRandom
      private var token: String = nextToken
      private val serverSocketHolder = new AtomicReference[ServerSocket]

      val serverThread = new Thread("sbt-socket-server") {
        override def run(): Unit = {
          Try {
            connection.connectionType match {
              case ConnectionType.Local if isWindows =>
                // Named pipe already has an exclusive lock.
                addServerError(
                  new Win32NamedPipeServerSocket(
                    pipeName,
                    connection.useJni,
                    connection.windowsServerSecurityLevel
                  )
                )
              case ConnectionType.Local =>
                val maxSocketLength =
                  UnixDomainSocketLibraryProvider.maxSocketLength(connection.useJni) - 1
                val path = socketfile.getAbsolutePath
                if (path.length > maxSocketLength)
                  sys.error(
                    "socket file absolute path too long; " +
                      "either switch to another connection type " +
                      "or define a short \"SBT_GLOBAL_SERVER_DIR\" value. " +
                      s"Current path: ${path}"
                  )
                tryClient(new UnixDomainSocket(path, connection.useJni))
                prepareSocketfile()
                addServerError(new UnixDomainServerSocket(path, connection.useJni))
              case ConnectionType.Tcp =>
                tryClient(new Socket(InetAddress.getByName(host), port))
                addServerError(new ServerSocket(port, 50, InetAddress.getByName(host)))
            }
          } match {
            case Failure(e) => p.failure(e)
            case Success(serverSocket) =>
              serverSocket.setSoTimeout(5000)
              serverSocketHolder.getAndSet(serverSocket) match {
                case null =>
                case s    => s.close()
              }
              log.info(s"sbt server started at ${connection.shortName}")
              writePortfile()
              if (connection.bspEnabled) {
                log.debug("Writing bsp connection file")
                BuildServerConnection.writeConnectionFile(
                  appConfiguration.provider.id.version,
                  appConfiguration.baseDirectory
                )
              }
              running.set(true)
              p.success(())
              while (running.get()) {
                try {
                  val socket = serverSocket.accept()
                  onIncomingSocket(socket, self)
                } catch {
                  case e: IOException if e.getMessage.contains("connect") =>
                  case _: SocketTimeoutException                          => // its ok
                  case _: SocketException if !running.get => // the server is shutting down
                }
              }
              serverSocketHolder.get match {
                case null =>
                case s    => s.close()
              }
          }
        }
      }
      serverThread.start()

      // Try the socket as a client to make sure that the server is not already up.
      // f tries to connect to the server, and flip the result.
      def tryClient(f: => Socket): Unit = {
        if (portfile.exists) {
          Try { f } match {
            case Failure(_) => ()
            case Success(socket) =>
              socket.close()
              throw new AlreadyRunningException()
          }
        } else ()
      }

      def addServerError(f: => ServerSocket): ServerSocket =
        ErrorHandling.translate(s"server failed to start on ${connection.shortName}. ") {
          f
        }

      override def authenticate(challenge: String): Boolean = synchronized {
        if (token == challenge) {
          token = nextToken
          writeTokenfile()
          true
        } else false
      }

      /** Generates 128-bit non-negative integer, and represent it as decimal string. */
      private def nextToken: String = {
        new BigInteger(128, rand).toString
      }

      override def shutdown(): Unit = {
        log.info("shutting down sbt server")
        if (portfile.exists) {
          IO.delete(portfile)
        }
        if (tokenfile.exists) {
          IO.delete(tokenfile)
        }
        running.set(false)
        serverSocketHolder.getAndSet(null) match {
          case null =>
          case s    => s.close()
        }
      }

      private def writeTokenfile(): Unit = {
        import JsonProtocol.*

        val uri = connection.shortName
        val t = TokenFile(uri, token)
        val jsonToken = Converter.toJson(t).get

        if (tokenfile.exists) {
          IO.delete(tokenfile)
        }
        IO.touch(tokenfile)
        ownerOnly(tokenfile)
        IO.write(tokenfile, CompactPrinter(jsonToken), IO.utf8, true)
      }

      /** Set the permission of the file such that the only the owner can read/write it. */
      private def ownerOnly(file: File): Unit = {
        def acl(owner: UserPrincipal) = {
          val builder = AclEntry.newBuilder
          builder.setPrincipal(owner)
          builder.setPermissions(AclEntryPermission.values()*)
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
      private def writePortfile(): Unit = {
        import JsonProtocol.*

        val uri = connection.shortName
        val p =
          auth match {
            case _ if auth(ServerAuthentication.Token) =>
              writeTokenfile()
              PortFile(uri, Option(tokenfile.toString), Option(IO.toURI(tokenfile).toString))
            case _ =>
              PortFile(uri, None, None)
          }
        val json = Converter.toJson(p).get
        IO.write(portfile, CompactPrinter(json))
      }

      private[sbt] def prepareSocketfile(): Unit = {
        if (socketfile.exists) {
          IO.delete(socketfile)
        }
        IO.createDirectory(socketfile.getParentFile)
      }
    }
}

private[sbt] case class ServerConnection(
    connectionType: ConnectionType,
    host: String,
    port: Int,
    auth: Set[ServerAuthentication],
    portfile: File,
    tokenfile: File,
    socketfile: File,
    pipeName: String,
    appConfiguration: AppConfiguration,
    windowsServerSecurityLevel: Int,
    useJni: Boolean,
    bspEnabled: Boolean,
) {
  def shortName: String = {
    connectionType match {
      case ConnectionType.Local if isWindows => s"local:$pipeName"
      case ConnectionType.Local              => s"local://$socketfile"
      case ConnectionType.Tcp                => s"tcp://$host:$port"
      // case ConnectionType.Ssh                => s"ssh://$host:$port"
    }
  }
}

private[sbt] class AlreadyRunningException extends IOException("sbt server is already running.")
