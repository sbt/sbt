/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.internal.langserver.SbtExecParams
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.io.IO
import sbt.io.syntax.*
import sbt.protocol.ExecStatusEvent
import sbt.protocol.codec.JsonProtocol.given

import scala.concurrent.duration.*

/**
 * A server that a second one displaces exits on its own, and a task in flight does not stop it
 * seeing that: it exits once the task ends.
 */
class PortfileBusyTest extends AbstractServerTest:
  override val testDirectory: String = "client"

  private val settle = 30.seconds

  test("a portfile that names another server, while a task runs") {
    val slowId = svr.session.nextId()
    svr.session.sendJsonRpc(slowId, "sbt/exec", SbtExecParams("slowTask")).get
    Thread.sleep(1000)
    val portfile = svr.baseDirectory / "project" / "target" / "active.json"
    IO.write(portfile, """{"uri":"local:///displaced","serverId":"another-server"}""")
    val done = svr.session.waitForResultInResponseMsg[ExecStatusEvent](settle, slowId).get
    assert(done.status == "Done", s"the task finished: ${done.status}")
    assert(waitUntil(settle)(!svr.isAlive), "the displaced server exits")
  }
