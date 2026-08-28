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
 * A project load closes the file tree repository and installs a new one, so a portfile watch that
 * a load does not outlive stops working after the first reload.
 */
class PortfileReloadTest extends AbstractServerTest {
  override val testDirectory: String = "client"

  private val settle = 30.seconds

  test("a portfile that names another server, after a reload") {
    assert(runBatchClient("reload") == 0, "reload must succeed")
    val portfile = svr.baseDirectory / "project" / "target" / "active.json"
    IO.write(portfile, """{"uri":"local:///displaced","serverId":"another-server"}""")
    assert(waitUntil(settle)(!svr.isAlive), "the displaced server exits")
  }
}
