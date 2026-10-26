/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package protocol

import java.io.{ File, IOException }
import java.lang.reflect.{ Constructor, InvocationTargetException }
import java.net.{ InetAddress, Socket, StandardProtocolFamily, URI, UnixDomainSocketAddress }
import java.nio.channels.SocketChannel
import java.nio.file.{ Path, Paths }
import scala.util.control.NonFatal
import sjsonnew.BasicJsonProtocol
import sjsonnew.support.scalajson.unsafe.{ Parser, Converter }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sbt.internal.protocol.{ PortFile, TokenFile }
import sbt.internal.protocol.codec.{ PortFileFormats, TokenFileFormats }
import sbt.internal.util.Util.isWindows

import scala.util.{ Failure, Success, Try }

object ClientSocket:
  private lazy val fileFormats = new BasicJsonProtocol with PortFileFormats with TokenFileFormats {}

  /** Thrown when a server connection file can't be read or parsed as JSON. */
  final class ConnectionFileReadException(file: File, cause: Throwable)
      extends Exception(s"sbt connection file $file is corrupt or unreadable: $cause", cause)

  def socket(portfile: File): (Socket, Option[String]) = socket(portfile, false)

  /** Parses the connection file written by the server. */
  private[sbt] def loadPortFile(portfile: File): Try[PortFile] =
    import fileFormats.given
    val parsed = Try(sbt.io.IO.read(portfile)).flatMap(Parser.parseFromString)
    parsed.flatMap(Converter.fromJson[PortFile])

  def socket(portfile: File, useJNI: Boolean): (Socket, Option[String]) =
    val p = readPortFile(portfile)
    val uri = new URI(p.uri)
    val token = readToken(p)
    (connect(uri, useJNI), token)

  private def readPortFile(portfile: File): PortFile = loadPortFile(portfile) match
    case Success(p) => p
    case Failure(e) => throw new ConnectionFileReadException(portfile, e)

  private def readToken(p: PortFile): Option[String] =
    import fileFormats.given
    p.tokenfilePath map { tp =>
      val tokeFile = new File(tp)
      try
        val json: JValue = Parser.parseFromFile(tokeFile).get
        Converter.fromJson[TokenFile](json).get.token
      catch case NonFatal(e) => throw new ConnectionFileReadException(tokeFile, e)
    }

  /** Reads the token that the portfile names, if it names one. */
  private[sbt] def token(portfile: File): Option[String] = readToken(readPortFile(portfile))

  private def connect(uri: URI, useJNI: Boolean): Socket =
    uri.getScheme match
      case "local" if !uri.isOpaque => unixSocket(localPath(uri))
      case "local"                  => localSocket(uri.getSchemeSpecificPart, useJNI)
      case "tcp"                    => new Socket(InetAddress.getByName(uri.getHost), uri.getPort)
      case _                        => sys.error(s"Unsupported uri: $uri")

  /** Whether a server still accepts connections on `uri`, as written in its connection file. */
  private[sbt] def reachable(uri: String, useJNI: Boolean): Boolean =
    try
      connect(new URI(uri), useJNI).close()
      true
    catch case NonFatal(_) => false
  def localSocket(name: String, useJNI: Boolean): Socket =
    if isWindows then namedPipeSocket(name, useJNI)
    else unixSocket(Paths.get(name))

  /**
   * ipcsocket's named pipe client, which sbtn for Windows bundles to reach sbt servers before
   * 2.1.0; the JVM artifacts do not depend on ipcsocket.
   */
  private[sbt] lazy val namedPipeConstructor: Option[Constructor[? <: Socket]] =
    try
      Some(
        Class
          .forName("org.scalasbt.ipcsocket.Win32NamedPipeSocket")
          .asSubclass(classOf[Socket])
          .getConstructor(classOf[String], java.lang.Boolean.TYPE)
      )
    catch case _: ReflectiveOperationException | _: LinkageError => None

  private def namedPipeSocket(name: String, useJNI: Boolean): Socket =
    namedPipeConstructor match
      case Some(c) =>
        try c.newInstance(s"\\\\.\\pipe\\$name", Boolean.box(useJNI))
        catch case e: InvocationTargetException => throw e.getCause
      case None =>
        throw new IOException(s"named pipe $name is only reachable from sbtn for Windows")

  def bootSocket(path: String): Socket = unixSocket(Paths.get(path))

  /**
   * The socket file that a hierarchical `local:` URI names, such as `local:///path/to/sock` or
   * `local:///C:/path/to/sock`; an opaque `local:name` names a Windows named pipe instead.
   */
  private[sbt] def localPath(uri: URI): Path =
    Paths.get(new URI("file", null, uri.getPath, null))

  private[sbt] def unixSocket(path: Path): Socket =
    val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
    try ch.connect(UnixDomainSocketAddress.of(path))
    catch
      case e: Throwable =>
        ch.close()
        throw e
    DuplexChannels.newSocket(ch)
end ClientSocket
