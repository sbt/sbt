/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration.*
import sbt.io.IO
import sbt.io.syntax.*
import sbt.internal.langserver.{ SbtExecParams, SbtShutdownParams }
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.protocol.{ ExecStatusEvent, ServerSession }
import sbt.protocol.codec.JsonProtocol.given

/**
 * A forced `sbt/shutdown` cannot interrupt a running command, only a task, so it waits for the
 * command to finish. While it waits, a second forced request is accepted as well. The server
 * exits once the command completes.
 */
class ShutdownForceCommandTest extends AbstractServerTest:
  override val testDirectory: String = "shutdown"
  private def portfile = svr.baseDirectory / "project" / "target" / "active.json"

  private def connect(): ServerSession =
    val session = ServerSession.connect(portfile)
    session.initialize(10.seconds, subscribeToAll = false).get
    session

  private def forceShutdown(session: ServerSession): ExecStatusEvent =
    val id = session.nextId()
    session.sendJsonRpc(id, "sbt/shutdown", SbtShutdownParams(Some(true))).get
    session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, id).get

  test("a forced shutdown waits for a running command and accepts a second force") {
    val slowId = svr.session.nextId()
    svr.session.sendJsonRpc(slowId, "sbt/exec", SbtExecParams("gatedSlowCommand")).get
    assert(
      waitUntil(10.seconds)((svr.baseDirectory / "started").exists),
      "gatedSlowCommand did not start"
    )

    val forcingClient = connect()
    assert(forceShutdown(forcingClient).status == "Processing")

    val lateClient = connect()
    assert(forceShutdown(lateClient).status == "Processing")

    assert(svr.isAlive, "the server exited while the command was still running")
    IO.touch(svr.baseDirectory / "release")
    val slowResponse =
      svr.session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, slowId).get
    assert(slowResponse.status == "Done", slowResponse.toString)

    assert(waitUntil(60.seconds)(!svr.isAlive), "the server did not exit after the shutdown")
  }
end ShutdownForceCommandTest
