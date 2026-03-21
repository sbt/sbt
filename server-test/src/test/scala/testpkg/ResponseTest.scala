/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.util.{ Failure, Success }
import sjsonnew.support.scalajson.unsafe.CompactPrinter

// starts svr using server-test/response and perform custom server tests
class ResponseTest extends AbstractServerTest {
  override val testDirectory: String = "response"

  test("response from a command") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "foo/export", "{}").get
    val response = svr.session.waitForResponseMsg(10.seconds, id).get
    assert(response.result.exists(r => CompactPrinter(r).contains("scala-library-2.12.21.jar")))
  }

  test("response from a task") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "foo/rootClasspath", "{}").get
    val response = svr.session.waitForResponseMsg(10.seconds, id).get
    assert(response.result.exists(r => CompactPrinter(r).contains("scala-library-2.12.21.jar")))
  }

  test("a command failure") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "foo/fail", "{}").get
    val response = svr.session.waitForResponseMsg(10.seconds, id).get
    assert(response.error.exists(err => err.code == -33000 && err.message == "fail message"))
  }

  test("a command failure with custom code") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "foo/customfail", "{}").get
    val response = svr.session.waitForResponseMsg(10.seconds, id).get
    assert(response.error.exists(err => err.code == 500 && err.message == "some error"))
  }

  test("a command with a notification") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "foo/notification", "{}").get
    svr.session
      .waitForNotificationMsg(10.seconds) { n =>
        n.method == "foo/something" &&
        n.params.exists(p => CompactPrinter(p) == "\"something\"")
      }
      .get
  }

  test("respond concurrently from a task and the handler") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "foo/respondTwice", "{}").get
    svr.session.waitForResponseMsg(10.seconds, id).get
    // the second response should never be sent
    neverReceiveResponseWithId(500.milliseconds, id)
  }

  test("concurrent result and error") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "foo/resultAndError", "{}").get
    svr.session.waitForResponseMsg(10.seconds, id).get
    // the second response (result or error) should never be sent
    neverReceiveResponseWithId(500.milliseconds, id)
  }

  test("response to a notification should not be sent") {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, "foo/customNotification", "{}").get
    neverReceiveResponse(500.milliseconds) { r =>
      r.result.exists(v => CompactPrinter(v) == "\"notification result\"")
    }
  }

  private def neverReceiveResponse(
      duration: FiniteDuration
  )(predicate: sbt.internal.protocol.JsonRpcResponseMessage => Boolean): Unit =
    svr.session.waitForResponseMsg(duration)(predicate) match {
      case Success(matched) =>
        fail(s"Expected no matching response, but received: $matched")
      case Failure(_: TimeoutException) => ()
      case Failure(e)                   => throw e
    }

  private def neverReceiveResponseWithId(duration: FiniteDuration, id: String): Unit =
    svr.session.waitForResponseMsg(duration, id) match {
      case Success(matched) =>
        fail(s"Expected no response for request $id, but received: $matched")
      case Failure(_: TimeoutException) => ()
      case Failure(e)                   => throw e
    }
}
