/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration.*
import sbt.internal.langserver.SbtExecParams
import sbt.protocol.ExecStatusEvent
import sbt.protocol.codec.JsonProtocol.given
import sbt.internal.langserver.codec.JsonProtocol.given

class QueuedNotificationTest extends AbstractServerTest {
  override val testDirectory: String = "queued"

  test("send Queued notification when command is queued behind another") {
    val slowId = svr.session.nextId()
    svr.session.sendJsonRpc(slowId, "sbt/exec", SbtExecParams("slowTask")).get

    Thread.sleep(500)

    val quickId = svr.session.nextId()
    svr.session.sendJsonRpc(quickId, "sbt/exec", SbtExecParams("quickTask")).get

    svr.session
      .waitForParamsInNotificationMsg[ExecStatusEvent](10.seconds) { s =>
        s.status == "Queued" && s.message.contains("waiting for: slowTask")
      }
      .get

    val slowResponse =
      svr.session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, slowId).get
    assert(slowResponse.status == "Done")

    val quickResponse =
      svr.session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, quickId).get
    assert(quickResponse.status == "Done")
  }
}
