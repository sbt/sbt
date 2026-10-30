/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration.*
import sbt.io.syntax.*
import sbt.internal.langserver.{ CancelRequestParams, ErrorCodes, SbtExecParams, SbtShutdownParams }
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.internal.protocol.JsonRpcResponseMessage
import sbt.io.IO
import sbt.protocol.{ ExecInfo, ExecStatusEvent, ServerSession }
import sbt.protocol.codec.JsonProtocol.given
import sjsonnew.support.scalajson.unsafe.Converter

trait ShutdownServerTest extends AbstractServerTest:
  override val testDirectory: String = "shutdown"
  private def portfile = svr.baseDirectory / "project" / "target" / "active.json"

  protected def connect(): ServerSession =
    val session = ServerSession.connect(portfile)
    session.initialize(10.seconds, subscribeToAll = false).get
    session

  /**
   * Waits for the `shutdown` notification the server sends to `session` just before it closes the
   * connection, and returns its params `(logShutdown, remainingCommands)`.
   *
   * @see [[sbt.internal.server.NetworkChannel#shutdown]]
   */
  protected def shutdownNotification(session: ServerSession): (Boolean, Option[(String, String)]) =
    import sjsonnew.BasicJsonProtocol.*
    val message = session.waitForNotificationMsg(15.seconds)(_.method == "shutdown").get
    Converter.fromJson[(Boolean, Option[(String, String)])](message.params.get).get

  /** Starts `command` and returns its exec id once `sbt/execs` reports it running. */
  protected def startBlocking(command: String): String =
    val execId = svr.session.nextId()
    svr.session.sendJsonRpc(execId, "sbt/exec", SbtExecParams(command)).get
    val running =
      waitUntil(10.seconds)(execs().exists(i => i.id == execId && i.status == "running"))
    assert(running, s"$command did not start")
    execId
end ShutdownServerTest

/**
 * A forced `sbt/shutdown` cancels the running command and takes over a graceful shutdown that was waiting behind it.
 */
class ShutdownForceTest extends ShutdownServerTest:
  test("a forced shutdown cancels the running task and takes over the waiting graceful shutdown") {
    val slowId = startBlocking("gatedSlowTask")

    val gracefulClient = connect()
    val gracefulId = gracefulClient.nextId()
    gracefulClient.sendJsonRpc(gracefulId, "sbt/shutdown", SbtShutdownParams(Some(false))).get
    val graceful = gracefulClient
      .waitForParamsInNotificationMsg[ExecStatusEvent](10.seconds)(_.execId.contains(gracefulId))
      .get
    assert(graceful.status == "Queued", graceful.toString)

    val forcingClient = connect()
    val forceId = forcingClient.nextId()
    forcingClient.sendJsonRpc(forceId, "sbt/shutdown", SbtShutdownParams(Some(true))).get

    val slowResponse = svr.session.waitForResponseMsg(10.seconds, slowId).get
    assert(
      slowResponse.error.exists(e =>
        e.code == ErrorCodes.UnknownError && e.message == "gatedSlowTask"
      ),
      slowResponse.toString
    )
    assert((svr.baseDirectory / "interrupted").exists, "gatedSlowTask was not interrupted")

    assert(shutdownNotification(forcingClient) == (false, None), "the forced shutdown did not run")
    assert(
      shutdownNotification(gracefulClient) == (true, None),
      "the graceful shutdown ran instead of being taken over"
    )
    assert(waitUntil(10.seconds)(!gracefulClient.isRunning), "the graceful client was not closed")
    assert(waitUntil(60.seconds)(!svr.isAlive), "the server did not exit after the shutdown")
  }
end ShutdownForceTest

/**
 * A forced `sbt/shutdown` sent while a command runs waits in the queue behind it. While it waits,
 * any client can remove it with `sbt/cancelRequest`, like any other queued exec, and the requester is told.
 */
class ShutdownForceCancelTest extends ShutdownServerTest:
  private def waitingExec(p: ExecInfo => Boolean): Option[ExecInfo] =
    execs().find(i => i.status == "waiting" && p(i))

  private def isCancelled(response: JsonRpcResponseMessage, by: String): Boolean =
    response.error.exists: e =>
      e.code == ErrorCodes.RequestCancelled && e.message == s"cancelled by $by"

  test("a forced shutdown answers the dropped execs and can be cancelled like any other exec") {
    // a running command and an exec waiting behind it
    val slowId = startBlocking("gatedCommand")
    val queuedId = svr.session.nextId()
    svr.session.sendJsonRpc(queuedId, "sbt/exec", SbtExecParams("about")).get
    assert(waitUntil(10.seconds)(waitingExec(_.id == queuedId).nonEmpty), "about was not queued")
    val queued = waitingExec(_.id == queuedId).get

    // a second client forces a shutdown; it waits behind gatedCommand
    val forcingClient = connect()
    val forceId = forcingClient.nextId()
    forcingClient.sendJsonRpc(forceId, "sbt/shutdown", SbtShutdownParams(Some(true))).get
    // read this answer before polling sbt/execs: the session drops responses it is not waiting for
    val dropped = svr.session.waitForResponseMsg(10.seconds, queuedId).get
    assert(
      waitUntil(10.seconds)(waitingExec(_.id == forceId).nonEmpty),
      "the forced shutdown was not queued"
    )
    val pendingForceInfo = waitingExec(_.id == forceId).get
    // the dropped exec was told who dropped it
    assert(isCancelled(dropped, s"shutdown from ${pendingForceInfo.clientName}"), dropped.toString)

    // cancels the waiting shutdown like any other exec
    val cancelId = svr.session.nextId()
    svr.session
      .sendJsonRpc(
        cancelId,
        "sbt/cancelRequest",
        CancelRequestParams(forceId, pendingForceInfo.channelName)
      )
      .get
    val cancelled =
      svr.session.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, cancelId).get
    assert(cancelled.status == "Task cancelled", cancelled.toString)
    assert(waitingExec(_.id == forceId).isEmpty, "the cancel did not remove the forced shutdown")
    // the forcing client was told its shutdown was cancelled, instead of hanging
    val refused = forcingClient.waitForResponseMsg(10.seconds, forceId).get
    assert(isCancelled(refused, queued.clientName), refused.toString)

    // the running command finishes normally and the server stays up
    IO.touch(svr.baseDirectory / "release")
    assert(svr.session.waitForResponseMsg(10.seconds, slowId).isSuccess, "gatedCommand hung")
    assert(svr.isAlive, "the server exited although the shutdown was cancelled")
  }
end ShutdownForceCancelTest
