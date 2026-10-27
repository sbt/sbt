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
class EventsTest extends AbstractServerTest:
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
      .sendJsonRpc(cancelId, "sbt/cancelRequest", CancelRequestParams(invalidID))
      .get
    val response = svr.session.waitForResponseMsg(20.seconds, cancelId).get
    assert(response.error.exists(_.code == -32800))

    // cancel the actual blockForever task so it doesn't block subsequent tests
    val cleanupId = svr.session.nextId()
    svr.session.sendJsonRpc(cleanupId, "sbt/cancelRequest", CancelRequestParams(id)).get
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
        CancelRequestParams(id),
        11.seconds
      )
      .get
    assert(cancelResult.status == "Task cancelled")
  }
  private def cancelNested(command: String, ready: String, interrupted: List[String]): Unit =
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "sbt/exec", SbtExecParams(command)).get
    assert(waitUntil(10.seconds)(testPath.resolve(ready).toFile.exists()))
    val cancelled = svr.session
      .sendJsonRpcAwaitResult[ExecStatusEvent](
        "sbt/cancelRequest",
        CancelRequestParams(id),
        10.seconds
      )
      .get
    assert(cancelled.status == "Task cancelled")
    for marker <- interrupted do
      assert(waitUntil(10.seconds)(testPath.resolve(marker).toFile.exists()), marker)
    assert(!testPath.resolve("outer-completed").toFile.exists())
    svr.session.waitForResponseMsg(10.seconds, id).get
    val response = svr.session
      .sendJsonRpcAwaitResult[ExecStatusEvent]("sbt/exec", SbtExecParams("show name"), 10.seconds)
      .get
    assert(response.status == "Done")

  test("cancel outer task after a nested evaluation completes"):
    cancelNested("nested", "nested-ready", List("nested-interrupted"))

  test("cancel inner and outer tasks during a nested evaluation"):
    cancelNested("during", "inner-ready", List("inner-interrupted", "outer-interrupted"))
  test("retry cancellation after the outer task swallows its first interrupt"):
    val ready = testPath.resolve("inner-ready").toFile
    sbt.io.IO.delete(ready)
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "sbt/exec", SbtExecParams("duringRetry")).get
    assert(waitUntil(10.seconds)(ready.exists()))
    def cancel(): Unit =
      val result = svr.session
        .sendJsonRpcAwaitResult[ExecStatusEvent](
          "sbt/cancelRequest",
          CancelRequestParams(id),
          10.seconds
        )
        .get
      assert(result.status == "Task cancelled")
    cancel()
    assert(waitUntil(10.seconds)(testPath.resolve("retry-first-interrupted").toFile.exists()))
    sbt.io.IO.touch(testPath.resolve("retry-resume").toFile)
    assert(waitUntil(10.seconds)(testPath.resolve("retry-ready").toFile.exists()))
    cancel()
    assert(waitUntil(10.seconds)(testPath.resolve("retry-interrupted").toFile.exists()))
end EventsTest
