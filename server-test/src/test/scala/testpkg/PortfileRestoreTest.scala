/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.File
import java.nio.file.Files

import sbt.io.IO
import sbt.io.syntax.*

import scala.concurrent.duration.*

/**
 * A server whose socket is gone keeps running and no client can reach it. A client that gives
 * up deletes the portfile and starts a server of its own, and this covers what each of them
 * does with that file.
 */
class PortfileRestoreTest extends AbstractServerTest:
  override val testDirectory: String = "client"

  private val settle = 30.seconds

  private def portfile: File = svr.baseDirectory / "project" / "target" / "active.json"

  /** The socket the portfile names. Deleting it leaves the server running and unreachable. */
  private def socketfile: File =
    val uri = """"uri":"local://([^"]+)"""".r
      .findFirstMatchIn(IO.read(portfile))
      .map(_.group(1))
      .getOrElse(fail(s"the portfile must name a local socket: ${IO.read(portfile)}"))
    new File(uri)

  /** An sbt script that records being run instead of starting a server. */
  private def scriptWriting(marker: File): String =
    val f = Files.createTempFile("fake-sbt", ".sh")
    Files.writeString(f, s"#!/usr/bin/env bash\ntouch ${marker.toString}\nsleep 600\n")
    f.toFile.setExecutable(true)
    f.toString

  test("a client that cannot reach the server the portfile names") {
    val marker = Files.createTempDirectory("started").toFile / "started.txt"
    val script = scriptWriting(marker)
    IO.delete(socketfile)
    IO.delete(portfile)
    assert(!waitUntil(settle)(portfile.exists), "the portfile stays deleted")
    /*
     * The client fails either way, having spent its attempts on a server it cannot reach. What
     * matters is that the restored file does not stop it starting a server of its own: it looks
     * for that file before the server has written it back.
     */
    scala.util.Try(runBatchClient(s"--sbt-script=$script", "willSucceed"))
    assert(marker.exists, "the client starts a server of its own")
  }
end PortfileRestoreTest
