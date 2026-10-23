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
import sbt.internal.langserver.{ LogMessageParams, SbtExecParams, SbtShutdownParams }
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.internal.protocol.JsonRpcRequestMessage
import sbt.protocol.{ ExecStatusEvent, ServerSession }
import sbt.protocol.codec.JsonProtocol.given

/**
 * A graceful `sbt/shutdown` waits for the running command like `sbt/exec shutdown` does. The
 * server advertises the request in its capabilities, and a second graceful request is accepted
 * as well. The server exits once the running command completes.
 */
class ShutdownRequestTest extends AbstractServerTest:
  override val testDirectory: String = "shutdown"
  private def portfile = svr.baseDirectory / "project" / "target" / "active.json"

  test("a graceful shutdown request queues behind the running command") {
    val slowId = svr.session.nextId()
    svr.session.sendJsonRpc(slowId, "sbt/exec", SbtExecParams("gatedSlowTask")).get
    svr.session
      .waitForParamsInNotificationMsg[LogMessageParams](5.seconds) { p =>
        p.message.contains("gatedSlowTask started")
      }
      .get

    val shutdownId = svr.session.nextId()
    svr.session.sendJsonRpc(JsonRpcRequestMessage("2.0", shutdownId, "sbt/shutdown", None)).get
    val queued =
      svr.session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, shutdownId).get
    assert(queued.status == "Queued", queued.toString)
    assert(queued.message.contains("waiting for: gatedSlowTask"), queued.toString)
    assert(queued.execId.exists(_.nonEmpty), queued.toString)

    val other = ServerSession.connect(portfile)
    val capabilities = other.initialize(10.seconds, subscribeToAll = false).get.capabilities
    assert(capabilities.shutdownRequest.contains(true), capabilities.toString)
    val secondId = other.nextId()
    other.sendJsonRpc(secondId, "sbt/shutdown", SbtShutdownParams(Some(false))).get
    val second = other.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, secondId).get
    assert(second.status == "Queued", second.toString)

    IO.touch(svr.baseDirectory / "release")
    val slowResponse =
      svr.session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, slowId).get
    assert(slowResponse.status == "Done")

    assert(
      shutdownNotification(svr.session) == (false, None),
      "the first graceful shutdown did not run"
    )
    assert(
      shutdownNotification(other) == (true, None),
      "the second graceful shutdown ran instead of the first"
    )
    assert(waitUntil(60.seconds)(!svr.isAlive), "the server did not exit after the shutdown")
  }
end ShutdownRequestTest
