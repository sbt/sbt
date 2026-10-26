/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import hedgehog.{ Gen, Property, Range, Result }
import hedgehog.core.{ ShrinkLimit, SuccessCount }
import hedgehog.runner.*
import java.net.{ StandardProtocolFamily, URI, UnixDomainSocketAddress }
import java.nio.channels.ServerSocketChannel
import java.nio.file.{ Files, Path, Paths }
import scala.util.Using
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }
import sbt.internal.protocol.PortFile
import sbt.internal.protocol.codec.PortFileFormats
import sbt.internal.util.Util.isWindows
import sbt.io.IO

object ClientSocketLocalUriTest extends Properties:
  override def tests: List[Test] =
    List(
      propertyN("localPath: reads back the path of a local URI", propLocalPath, 50),
      example("localPath: reads a drive-letter path", driveLetterPath),
    ) ++ (
      if isWindows then Nil
      else
        List(
          example("socket: connects over AF_UNIX when the portfile names a path", connectsByPath),
          example("reachable: follows a path URI", reachableByPath),
        )
    )

  def propertyN(name: String, result: => Property, n: Int): Test =
    Test(name, result)
      .config(_.copy(testLimit = SuccessCount(n), shrinkLimit = ShrinkLimit(n * 10)))

  private val segmentGen: Gen[String] =
    Gen
      .string(
        Gen.frequency1(8 -> Gen.alphaNum, 1 -> Gen.constant(' '), 1 -> Gen.constant('-')),
        Range.linear(1, 12)
      )
      .map(_.trim)
      .filter(_.nonEmpty)

  def propLocalPath: Property =
    for segments <- segmentGen.list(Range.linear(1, 5)).log("segments")
    yield
      val root = Paths.get("").toAbsolutePath.getRoot
      val path = segments.foldLeft(root)(_.resolve(_))
      val uri = new URI("local", null, path.toUri.getPath, null)
      Result
        .assert(ClientSocket.localPath(uri) == path)
        .log(s"$uri read back as ${ClientSocket.localPath(uri)}")

  def driveLetterPath: Result =
    val read = ClientSocket.localPath(new URI("local:///C:/Users/u/.sbt/server/ab/sock"))
    val expected =
      if isWindows then Paths.get("C:\\Users\\u\\.sbt\\server\\ab\\sock")
      else Paths.get("/C:/Users/u/.sbt/server/ab/sock")
    Result.assert(read == expected).log(s"read $read")

  def connectsByPath: Result =
    withUnixServer: (dir, sock, server) =>
      val portfile = dir.resolve("active.json").toFile
      writePortfile(portfile, s"local://$sock")
      val (client, token) = ClientSocket.socket(portfile)
      Using.resource(client): client =>
        Using.resource(server.accept()): serverSide =>
          client.getOutputStream.write(Array[Byte](42))
          val bb = java.nio.ByteBuffer.allocate(1)
          while bb.hasRemaining do serverSide.read(bb)
          Result.all(
            List(
              Result.assert(bb.get(0) == 42.toByte).log("server got a different byte"),
              Result.assert(token.isEmpty).log(s"unexpected token $token"),
            )
          )

  def reachableByPath: Result =
    val (uri, whileUp) = withUnixServer: (_, sock, _) =>
      val uri = s"local://$sock"
      (uri, ClientSocket.reachable(uri, useJNI = false))
    Result.all(
      List(
        Result.assert(whileUp).log(s"$uri was unreachable while the server was up"),
        Result
          .assert(!ClientSocket.reachable(uri, useJNI = false))
          .log(s"$uri was reachable after the server closed"),
      )
    )

  private def withUnixServer[A](f: (Path, Path, ServerSocketChannel) => A): A =
    val dir = Files.createTempDirectory(Paths.get("/tmp"), "sbtcsu")
    try
      val sock = dir.resolve("sock")
      Using.resource(ServerSocketChannel.open(StandardProtocolFamily.UNIX)): server =>
        server.bind(UnixDomainSocketAddress.of(sock))
        f(dir, sock, server)
    finally IO.delete(dir.toFile)

  private def writePortfile(portfile: java.io.File, uri: String): Unit =
    val formats = new sjsonnew.BasicJsonProtocol with PortFileFormats {}
    import formats.given
    val json = Converter.toJson(PortFile(uri, None, None, Vector.empty, None, None)).get
    IO.write(portfile, CompactPrinter(json))
end ClientSocketLocalUriTest
