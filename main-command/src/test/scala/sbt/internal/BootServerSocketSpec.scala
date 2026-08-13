/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.nio.file.{ Files, Paths }
import sbt.internal.util.Util
import verify.BasicTestSuite

object BootServerSocketSpec extends BasicTestSuite:

  // the constructor only reads baseDirectory; provider is never touched
  private def config(base: java.io.File): xsbti.AppConfiguration =
    new xsbti.AppConfiguration {
      override def arguments(): Array[String] = Array.empty
      override def baseDirectory(): java.io.File = base
      override def provider(): xsbti.AppProvider = null
    }

  private def probe(location: String): Boolean =
    BootServerSocketProbe.liveServerDetected(location, false)

  private def freshBase(prefix: String): (java.io.File, Long) =
    val base = Files.createTempDirectory(prefix).toRealPath().toFile
    (base, base.getAbsolutePath.hashCode.toLong ^ System.nanoTime())

  test("a live boot server is detected by the probe") {
    val (base, token) = freshBase("boot-socket-live")
    val location = BootServerSocket.socketLocation(base.toPath, token)
    val server = new BootServerSocket(config(base), token)
    val live =
      try probe(location)
      finally server.close()
    assert(live)
  }

  test("the probe reports no live server when nothing is listening") {
    val (base, token) = freshBase("boot-socket-none")
    val location = BootServerSocket.socketLocation(base.toPath, token)
    val live = probe(location)
    assert(!live)
  }

  test("after close, the probe reports no live server") {
    val (base, token) = freshBase("boot-socket-closed")
    val location = BootServerSocket.socketLocation(base.toPath, token)
    val server = new BootServerSocket(config(base), token)
    server.close()
    val live = probe(location)
    assert(!live)
  }

  test("a stale socket file is not a live server and does not block a new socket") {
    if (!Util.isWindows) {
      val (base, token) = freshBase("boot-socket-stale")
      val location = Paths.get(BootServerSocket.socketLocation(base.toPath, token))
      Files.createDirectories(location.getParent)
      Files.createFile(location) // leftover from a killed process
      val staleLooksLive = probe(location.toString)
      assert(!staleLooksLive)
      val server = new BootServerSocket(config(base), token) // reclaims the path
      val liveAfterReclaim =
        try probe(location.toString)
        finally server.close()
      assert(liveAfterReclaim)
    }
  }

end BootServerSocketSpec
