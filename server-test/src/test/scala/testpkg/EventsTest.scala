/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration.*
import sbt.protocol.ExecStatusEvent
import sbt.protocol.codec.JsonProtocol.given
import sbt.internal.langserver.{ LogMessageParams, SbtExecParams, CancelRequestParams }
import sbt.internal.langserver.codec.JsonProtocol.given

// starts svr using server-test/events and perform event related tests
class EventsTest extends AbstractServerTest {
  override val testDirectory: String = "events"

  test("report task failures in case of exceptions") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "sbt/exec", SbtExecParams("hello")).get
    val response = svr.session.waitForResponseMsg(10.seconds, id).get
    assert(response.error.nonEmpty)
  }

  test("return error if cancelling non-matched task id") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "sbt/exec", SbtExecParams("blockForever")).get

    Thread.sleep(1000)

    val invalidID = svr.session.nextId()
    val cancelId = svr.session.nextId()
    svr.session
      .sendJsonRpc(cancelId, "sbt/cancelRequest", CancelRequestParams(invalidID.toString))
      .get
    val response = svr.session.waitForResponseMsg(20.seconds, cancelId).get
    assert(response.error.exists(_.code == -32800))

    // cancel the actual blockForever task so it doesn't block subsequent tests
    val cleanupId = svr.session.nextId()
    svr.session.sendJsonRpc(cleanupId, "sbt/cancelRequest", CancelRequestParams(id.toString)).get
    svr.session.waitForResponseMsg(10.seconds, cleanupId).get
  }

  test("cancel on-going task") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "sbt/exec", SbtExecParams("blockForever")).get
    svr.session
      .waitForParamsInNotificationMsg[LogMessageParams](10.seconds) { p =>
        p.message.contains("Processing sbt/exec")
      }
      .get

    Thread.sleep(1000)

    val cancelResult = svr.session
      .sendJsonRpcAwaitResult[ExecStatusEvent](
        "sbt/cancelRequest",
        CancelRequestParams(id.toString),
        11.seconds
      )
      .get
    assert(cancelResult.status == "Task cancelled")
  }
}
