/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package protocol

import java.io.{ File, InputStream, OutputStream }
import java.net.{ InetAddress, Socket, StandardProtocolFamily, URI, UnixDomainSocketAddress }
import java.nio.channels.{ Channels, SocketChannel }
import scala.util.control.NonFatal
import sjsonnew.BasicJsonProtocol
import sjsonnew.support.scalajson.unsafe.{ Parser, Converter }
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sbt.internal.protocol.{ PortFile, TokenFile }
import sbt.internal.protocol.codec.{ PortFileFormats, TokenFileFormats }
import sbt.internal.util.Util.isWindows
import org.scalasbt.ipcsocket.*

object ClientSocket {
  private lazy val fileFormats = new BasicJsonProtocol with PortFileFormats with TokenFileFormats {}

  /** Thrown when a server connection file can't be read or parsed as JSON. */
  final class ConnectionFileReadException(file: File, cause: Throwable)
      extends Exception(s"sbt connection file $file is corrupt or unreadable: $cause", cause)

  def socket(portfile: File): (Socket, Option[String]) = socket(portfile, false)
  def socket(portfile: File, useJNI: Boolean): (Socket, Option[String]) = {
    import fileFormats.given
    val p =
      try
        val json: JValue = Parser.parseFromString(sbt.io.IO.read(portfile)).get
        Converter.fromJson[PortFile](json).get
      catch case NonFatal(e) => throw new ConnectionFileReadException(portfile, e)
    val uri = new URI(p.uri)
    // println(uri)
    val token = p.tokenfilePath map { tp =>
      val tokeFile = new File(tp)
      try
        val json: JValue = Parser.parseFromFile(tokeFile).get
        Converter.fromJson[TokenFile](json).get.token
      catch case NonFatal(e) => throw new ConnectionFileReadException(tokeFile, e)
    }
    val sk = uri.getScheme match {
      case "local" => localSocket(uri.getSchemeSpecificPart, useJNI)
      case "tcp"   => new Socket(InetAddress.getByName(uri.getHost), uri.getPort)
      case _       => sys.error(s"Unsupported uri: $uri")
    }
    (sk, token)
  }
  def localSocket(name: String, useJNI: Boolean): Socket =
    if (isWindows) new Win32NamedPipeSocket(s"\\\\.\\pipe\\$name", useJNI)
    else new UnixDomainSocket(name, useJNI)

  def bootSocket(path: String): Socket =
    val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
    ch.connect(UnixDomainSocketAddress.of(path))
    new Socket:
      private val in = Channels.newInputStream(ch)
      private val out = Channels.newOutputStream(ch)
      override def getInputStream: InputStream = in
      override def getOutputStream: OutputStream = out
      override def close(): Unit = ch.close()
      override def isClosed: Boolean = !ch.isOpen
}
