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
import sbt.internal.langserver.{ ErrorCodes, LogMessageParams, SbtExecParams, SbtShutdownParams }
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.protocol.{ ExecStatusEvent, ServerSession }
import sbt.protocol.codec.JsonProtocol.given
import sjsonnew.support.scalajson.unsafe.Converter

/**
 * A forced `sbt/shutdown` cancels the running command and takes over a graceful shutdown that
 * was waiting behind it.
 */
class ShutdownForceTest extends AbstractServerTest:
  override val testDirectory: String = "shutdown"
  private def portfile = svr.baseDirectory / "project" / "target" / "active.json"

  private def connect(): ServerSession =
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

  test("a forced shutdown cancels the running command and takes over the waiting graceful one") {
    val slowId = svr.session.nextId()
    svr.session.sendJsonRpc(slowId, "sbt/exec", SbtExecParams("gatedSlowTask")).get
    svr.session
      .waitForParamsInNotificationMsg[LogMessageParams](5.seconds) { p =>
        p.message.contains("gatedSlowTask started")
      }
      .get

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
    val forced =
      forcingClient.waitForResultInResponseMsg[ExecStatusEvent](10.seconds, forceId).get
    assert(forced.status == "Processing", forced.toString)
    assert(forced.execId.contains(forceId), forced.toString)

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
