/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration.*
import scala.util.Using
import sbt.io.syntax.*
import sbt.protocol.ServerSession
import sbt.internal.langserver.{ CancelRequestParams, InitializeParams, SbtExecParams }
import sbt.internal.protocol.{ InitializeOption, JsonRpcResponseMessage }
import sbt.protocol.{ ExecInfo, ExecStatusEvent, ExecsResponse }
import sjsonnew.JsonWriter
import sjsonnew.support.scalajson.unsafe.Converter
import sbt.protocol.codec.JsonProtocol.given
import sbt.internal.langserver.codec.JsonProtocol.given

class QueuedNotificationTest extends AbstractServerTest:
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

  private def startSlowAndQueueQuick(): (String, String) =
    val slowId = svr.session.nextId()
    svr.session.sendJsonRpc(slowId, "sbt/exec", SbtExecParams("slowTask")).get
    Thread.sleep(500)
    val quickId = svr.session.nextId()
    svr.session.sendJsonRpc(quickId, "sbt/exec", SbtExecParams("quickTask")).get
    Thread.sleep(500)
    (slowId, quickId)

  private def execs(): Vector[ExecInfo] =
    svr.session
      .sendJsonRpcAwaitResult[ExecsResponse]("sbt/execs", Map.empty[String, String], 10.seconds)
      .get
      .execs

  private def cancelAndCollect[A: JsonWriter](
      method: String,
      params: A,
      ownerId: String
  ): (JsonRpcResponseMessage, JsonRpcResponseMessage) =
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, method, params).get
    val responses = (1 to 2).map: _ =>
      svr.session.waitForResponseMsg(10.seconds)(r => r.id == id || r.id == ownerId).get
    (responses.find(_.id == id).get, responses.find(_.id == ownerId).get)

  test("sbt/execs lists the running and waiting execs, and sbt/cancelRequest removes one") {
    val (slowId, quickId) = startSlowAndQueueQuick()
    val listed = execs()
    assert(
      listed.map(e => (e.commandLine, e.status)) == Vector(
        "slowTask" -> "running",
        "quickTask" -> "waiting"
      )
    )
    assert(listed.map(_.id) == Vector(slowId, quickId))
    assert(listed.forall(e => e.channelName.startsWith("network") && e.clientName == e.channelName))
    val quick = listed(1)
    val params = CancelRequestParams(quick.id, Some(quick.channelName))
    val (cancelResponse, quickResponse) =
      cancelAndCollect("sbt/cancelRequest", params, quickId)
    assert(cancelResponse.error.isEmpty)
    assert(quickResponse.error.exists(_.code == -32800))
    assert(execs().map(_.commandLine) == Vector("slowTask"))

    val cancelAgainId = svr.session.nextId()
    svr.session.sendJsonRpc(cancelAgainId, "sbt/cancelRequest", params).get
    assert(svr.session.waitForResponseMsg(10.seconds, cancelAgainId).get.error.nonEmpty)

    val slowResponse =
      svr.session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, slowId).get
    assert(slowResponse.status == "Done")
  }

  test("sbt/cancelRequest removes the caller's own waiting exec") {
    val (slowId, quickId) = startSlowAndQueueQuick()
    val (cancelResponse, quickResponse) =
      cancelAndCollect("sbt/cancelRequest", CancelRequestParams(quickId), quickId)
    val cancelled = Converter.fromJson[ExecStatusEvent](cancelResponse.result.get).get
    assert(cancelled.status == "Task cancelled")
    assert(quickResponse.error.exists(_.code == -32800))
    val slowResponse =
      svr.session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, slowId).get
    assert(slowResponse.status == "Done")
  }

  private def connect(): ServerSession =
    val session = ServerSession.connect(svr.baseDirectory / "project" / "target" / "active.json")
    session.initialize(10.seconds, false).get
    session

  test("sbt/cancelRequest without channelName cancels another client's exec if the id is unique") {
    Using.resources(connect(), connect()) { (other1, other2) =>
      val slowId = svr.session.nextId()
      svr.session.sendJsonRpc(slowId, "sbt/exec", SbtExecParams("slowTask")).get
      Thread.sleep(500)
      val dupId = svr.session.nextId()
      other1.sendJsonRpc(dupId, "sbt/exec", SbtExecParams("quickTask")).get
      Thread.sleep(300)
      other2.sendJsonRpc(dupId, "sbt/exec", SbtExecParams("quickTask")).get
      Thread.sleep(500)

      def cancel(params: CancelRequestParams): JsonRpcResponseMessage =
        val id = svr.session.nextId()
        svr.session.sendJsonRpc(id, "sbt/cancelRequest", params).get
        svr.session.waitForResponseMsg(10.seconds, id).get

      assert(cancel(CancelRequestParams(dupId)).error.exists(_.code == -32602))
      val first = execs().find(_.id == dupId).get
      assert(cancel(CancelRequestParams(dupId, Some(first.channelName))).error.isEmpty)
      assert(other1.waitForResponseMsg(10.seconds, dupId).get.error.exists(_.code == -32800))
      assert(cancel(CancelRequestParams(dupId)).error.isEmpty)
      assert(other2.waitForResponseMsg(10.seconds, dupId).get.error.exists(_.code == -32800))

      val slowResponse =
        svr.session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, slowId).get
      assert(slowResponse.status == "Done")
    }
  }

  test("sbt/execsChanged is sent to clients that set subscribeToExecs") {
    val options = InitializeOption(None).withSkipAnalysis(true).withSubscribeToExecs(true)
    val params = InitializeParams(None, None, None, Some(Converter.toJson(options).get), None, None)
    Using.resource(
      ServerSession.connect(svr.baseDirectory / "project" / "target" / "active.json")
    ) { sub =>
      val initId = sub.nextId()
      sub.sendJsonRpc(initId, "initialize", params).get
      sub.waitForResponseMsg(10.seconds, initId).get
      def nextExecs(): Vector[(String, String)] =
        val n = sub.waitForNotificationMsg(10.seconds)(_.method == "sbt/execsChanged").get
        Converter
          .fromJson[ExecsResponse](n.params.get)
          .get
          .execs
          .map(e => e.commandLine -> e.status)
      assert(nextExecs() == Vector())
      val (slowId, quickId) = startSlowAndQueueQuick()
      val seen = Iterator.continually(nextExecs()).take(6).toVector
      assert(
        seen == Vector(
          Vector("slowTask" -> "waiting"),
          Vector("slowTask" -> "running"),
          Vector("slowTask" -> "running", "quickTask" -> "waiting"),
          Vector("quickTask" -> "waiting"),
          Vector("quickTask" -> "running"),
          Vector(),
        )
      )
      svr.session.waitForResponseMsg(10.seconds, slowId).get
      svr.session.waitForResponseMsg(10.seconds, quickId).get
      assert(svr.session.waitForNotificationMsg(1.second)(_.method == "sbt/execsChanged").isFailure)
    }
  }
end QueuedNotificationTest
