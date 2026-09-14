/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.io.IO
import sbt.io.syntax.*

import scala.concurrent.duration.*

/**
 * The portfile names the server that owns the socket. A second server binds the same socket path,
 * which leaves the first server holding a socket that no client can reach.
 */
class PortfileTest extends AbstractServerTest:
  override val testDirectory: String = "client"

  private val settle = 30.seconds

  private def portfile: File = svr.baseDirectory / "project" / "target" / "active.json"

  test("a portfile written by a server") {
    val id = """"serverId":"([^"]+)"""".r.findFirstMatchIn(IO.read(portfile)).map(_.group(1))
    assert(id.exists(_.nonEmpty), s"the portfile names its writer: ${IO.read(portfile)}")
  }

  test("a portfile deleted twice") {
    val published = IO.read(portfile)
    IO.delete(portfile)
    assert(waitUntil(settle)(portfile.exists), "the server writes its portfile again")
    assert(IO.read(portfile) == published, "the portfile still names this server")
    Thread.sleep(1000)
    IO.delete(portfile)
    assert(waitUntil(settle)(portfile.exists), "it writes the portfile again the second time")
  }

  test("a portfile that names another server") {
    val replacement = """{"uri":"local:///displaced","serverId":"another-server"}"""
    IO.write(portfile, replacement)
    assert(waitUntil(settle)(!svr.isAlive), "the displaced server exits")
    assert(IO.read(portfile) == replacement, "the displaced server does not delete the portfile")
  }
end PortfileTest
