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
import java.net.{
  InetAddress,
  ServerSocket,
  Socket,
  SocketException,
  SocketTimeoutException,
  StandardProtocolFamily,
  URI,
  UnixDomainSocketAddress
}
import java.nio.channels.ServerSocketChannel
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import java.security.SecureRandom
import java.math.BigInteger

import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success, Try }
import sbt.internal.client.NetworkClient
import sbt.internal.protocol.{ PortFile, TokenFile }
import sbt.util.Logger
import sbt.io.IO
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }
import sbt.internal.protocol.codec.*
import sbt.internal.util.ErrorHandling
import sbt.internal.util.Util.isMac
import sbt.internal.bsp.BuildServerConnection
import sbt.protocol.{ ClientSocket, DuplexChannels }
import xsbti.AppConfiguration

private[sbt] sealed trait ServerInstance:
  def shutdown(): Unit
  def serverId: String
  def ready: Future[Unit]
  def authenticate(challenge: String): Boolean

private[sbt] object Server:
  private val maxSocketLength = if isMac then 103 else 107

  sealed trait JsonProtocol
      extends sjsonnew.BasicJsonProtocol
      with PortFileFormats
      with TokenFileFormats
  object JsonProtocol extends JsonProtocol

  /** The id the portfile names, None when it names none, and a failure when unreadable. */
  private[sbt] def serverIdOf(portfile: File): Try[Option[String]] =
    ClientSocket.loadPortFile(portfile).map(_.serverId)

  def start(
      connection: ServerConnection,
      onIncomingSocket: (AtomicReference[Socket], ServerInstance) => Unit,
      log: Logger
  ): ServerInstance =
    new ServerInstance:
      self =>
      import connection.*
      val running = new AtomicBoolean(false)
      val p: Promise[Unit] = Promise[Unit]()
      val ready: Future[Unit] = p.future
      private val rand = new SecureRandom
      private var token: String = nextToken
      private val serverSocketHolder = AtomicCloseable[ServerSocket]()
      override val serverId: String = java.util.UUID.randomUUID().toString

      val serverThread = new Thread("sbt-socket-server"):
        override def run(): Unit =
          Try {
            connection.connectionType match
              case ConnectionType.Local =>
                val path = socketfile.getAbsolutePath
                if path.length > maxSocketLength then
                  sys.error(
                    "socket file absolute path too long; " +
                      "either switch to another connection type " +
                      "or define a short \"SBT_GLOBAL_SERVER_DIR\" value. " +
                      s"Current path: ${path}"
                  )
                tryClient(ClientSocket.unixSocket(socketfile.toPath))
                prepareSocketfile()
                addServerError(unixServerSocket(path))
              case ConnectionType.Tcp =>
                tryClient(new Socket(InetAddress.getByName(host), port))
                addServerError(new ServerSocket(port, 50, InetAddress.getByName(host)))
          } match
            case Failure(e)            => p.failure(e)
            case Success(serverSocket) =>
              serverSocket.setSoTimeout(5000)
              serverSocketHolder.set(serverSocket)
              log.debug(s"sbt server started at ${connection.shortName}")
              writePortfile()
              if connection.bspEnabled then
                log.debug("Writing bsp connection file")
                BuildServerConnection.writeConnectionFile(
                  appConfiguration.provider.id.version,
                  appConfiguration.baseDirectory
                )
              running.set(true)
              p.success(())
              while running.get() do
                val clientSocket = AtomicCloseable[Socket]()
                try
                  clientSocket.set(serverSocket.accept())
                  onIncomingSocket(clientSocket.ref, self)
                catch
                  case scala.util.control.NonFatal(e) if clientSocket.get ne null =>
                    log.error(s"sbt server failed to serve a client: $e")
                    log.trace(e)
                  case e: IOException if Option(e.getMessage).exists(_.contains("connect")) =>
                  case _: SocketTimeoutException          => // its ok
                  case _: SocketException if !running.get => // the server is shutting down
                clientSocket.close()
              serverSocketHolder.close()
      serverThread.start()

      // Try the socket as a client to make sure that the server is not already up.
      // f tries to connect to the server, and flip the result.
      def tryClient(f: => Socket): Unit =
        if portfile.exists then
          Try { f } match
            case Failure(_)      => ()
            case Success(socket) =>
              socket.close()
              throw new AlreadyRunningException()
        else ()

      def unixServerSocket(path: String): ServerSocket =
        val ch = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        try
          ch.bind(UnixDomainSocketAddress.of(path))
          DuplexChannels.newServerSocket(ch)
        catch
          case e: Throwable =>
            ch.close()
            throw e

      def addServerError(f: => ServerSocket): ServerSocket =
        ErrorHandling.translate(s"server failed to start on ${connection.shortName}. ") {
          f
        }

      override def authenticate(challenge: String): Boolean = synchronized {
        if token == challenge then
          token = nextToken
          writeTokenfile()
          true
        else false
      }

      /** Generates 128-bit non-negative integer, and represent it as decimal string. */
      private def nextToken: String =
        new BigInteger(128, rand).toString

      override def shutdown(): Unit =
        if serverIdOf(portfile).getOrElse(None).contains(serverId) then IO.delete(portfile)
        IO.delete(tokenfile)
        running.set(false)
        serverSocketHolder.close()
        log.info("shutting down sbt server")

      private def writeTokenfile(): Unit =
        import JsonProtocol.given

        val uri = connection.shortName
        val t = TokenFile(uri, token)
        val jsonToken = Converter.toJson(t).get

        IO.writeFileAtomically(tokenfile, ownerOnly = true)(tmp =>
          IO.write(tmp, CompactPrinter(jsonToken), IO.utf8, false)
        )

      // This file exists through the lifetime of the server.
      private def writePortfile(): Unit =
        import JsonProtocol.given

        val uri = connection.shortName
        // both variables reach everything this server starts, so only record them when
        // they name this build rather than the one whose client set them
        val startedByThisBuild = sys.env
          .get(NetworkClient.sysPropsPortfileEnv)
          .map(new File(_).getCanonicalFile)
          .contains(portfile.getCanonicalFile)
        val recorded = if startedByThisBuild then sys.env.get(NetworkClient.sysPropsEnv) else None
        val sysProps = recorded.toVector.flatMap(NetworkClient.decodeSysProps)
        // an empty list of options and no idea what the options are read the same, so say
        // which of the two this is: a client can restart a server over the first but has no
        // business taking down one whose options it never saw
        val sysPropsRecorded = Option(startedByThisBuild)
        val authOK = auth(ServerAuthentication.Token)
        if authOK then writeTokenfile()
        val p = PortFile(
          uri,
          if authOK then Some(tokenfile.toString) else None,
          if authOK then Some(IO.toURI(tokenfile).toString) else None,
          sysProps,
          sysPropsRecorded,
          Some(serverId)
        )
        val json = Converter.toJson(p).get
        IO.writeFileAtomically(portfile)(tmp => IO.write(tmp, CompactPrinter(json)))
      end writePortfile

      private[sbt] def prepareSocketfile(): Unit =
        if socketfile.exists then IO.delete(socketfile)
        IO.createDirectory(socketfile.getParentFile)
end Server

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
    useJni: Boolean,
    bspEnabled: Boolean,
):
  def shortName: String =
    connectionType match
      case ConnectionType.Local => ServerConnection.localUri(socketfile)
      case ConnectionType.Tcp   => s"tcp://$host:$port"
      // case ConnectionType.Ssh                => s"ssh://$host:$port"

private[sbt] object ServerConnection:
  /**
   * The `local:///...` URI of a socket file, with forward slashes and escapes, so that a Windows
   * path reads back as `local:///C:/...`.
   */
  def localUri(socketfile: File): String =
    new URI("local", "", socketfile.toPath.toAbsolutePath.toUri.getPath, null, null).toString

private[sbt] class AlreadyRunningException extends IOException("sbt server is already running.")
