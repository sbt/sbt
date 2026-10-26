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

import hedgehog.{ Gen, Property, Range, Result }
import hedgehog.runner.*
import java.net.URI
import java.nio.file.Paths
import sbt.protocol.ClientSocket

object ServerConnectionTest extends Properties:
  override def tests: List[Test] = List(
    property("localUri: the client reads back the socket file", propRoundTrip),
    example("localUri: keeps the local:/// form", keepsForm),
  )

  private val segmentGen: Gen[String] =
    Gen
      .string(
        Gen.frequency1(
          8 -> Gen.alphaNum,
          1 -> Gen.element1(' ', '-', '%', '#', '.'),
        ),
        Range.linear(1, 12)
      )
      .map(_.trim)
      .filter(s => s.nonEmpty && s != "." && s != "..")

  def propRoundTrip: Property =
    for segments <- segmentGen.list(Range.linear(1, 5)).log("segments")
    yield
      val root = Paths.get("").toAbsolutePath.getRoot
      val socketfile = segments.foldLeft(root)(_.resolve(_))
      val uri = ServerConnection.localUri(socketfile.toFile)
      val read = ClientSocket.localPath(new URI(uri))
      Result.all(
        List(
          Result.assert(!new URI(uri).isOpaque).log(s"$uri is opaque"),
          Result.assert(read == socketfile).log(s"$uri read back as $read"),
        )
      )

  def keepsForm: Result =
    val socketfile = Paths.get("").toAbsolutePath.getRoot.resolve("sbt").resolve("sock")
    val uri = ServerConnection.localUri(socketfile.toFile)
    Result.assert(uri.startsWith("local:///") && uri.endsWith("/sbt/sock")).log(uri)
end ServerConnectionTest
