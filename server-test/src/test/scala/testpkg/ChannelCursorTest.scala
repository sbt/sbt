/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration.*
import sbt.protocol.{ ExecStatusEvent, ServerSession }
import sbt.protocol.codec.JsonProtocol.given
import sbt.internal.langserver.{ LogMessageParams, SbtExecParams }
import sbt.internal.langserver.codec.JsonProtocol.given

class ChannelCursorTest extends AbstractServerTest {
  override val testDirectory: String = "channel-cursor"

  test("channel cursor - independent project cursors") {
    val portfile = testPath.resolve("project/target/active.json").toFile
    val session2 = ServerSession.connect(portfile)
    try {
      session2.initialize(10.seconds, subscribeToAll = false)

      val switchToAResult = svr.session
        .sendJsonRpcAwaitResult[ExecStatusEvent](
          "sbt/exec",
          SbtExecParams("project projectA")
        )
        .get
      assert(switchToAResult.status == "Done")

      val switchToBResult = session2
        .sendJsonRpcAwaitResult[ExecStatusEvent](
          "sbt/exec",
          SbtExecParams("project projectB")
        )
        .get
      assert(switchToBResult.status == "Done")

      val printA = svr.session.nextId()
      svr.session.sendJsonRpc(printA, "sbt/exec", SbtExecParams("printCurrentProject")).get
      val logA = svr.session
        .waitForParamsInNotificationMsg[LogMessageParams](30.seconds) { p =>
          p.message.startsWith("CURRENT_PROJECT_IS:")
        }
        .get
      assert(
        logA.message == "CURRENT_PROJECT_IS:project-a",
        "First channel should still be on projectA"
      )
      svr.session.waitForResultInResponseMsg[ExecStatusEvent](30.seconds, printA).get

      val printB = session2.nextId()
      session2.sendJsonRpc(printB, "sbt/exec", SbtExecParams("printCurrentProject")).get
      val logB = session2
        .waitForParamsInNotificationMsg[LogMessageParams](30.seconds) { p =>
          p.message.startsWith("CURRENT_PROJECT_IS:")
        }
        .get
      assert(
        logB.message == "CURRENT_PROJECT_IS:project-b",
        "Second channel should still be on projectB"
      )
      session2.waitForResultInResponseMsg[ExecStatusEvent](30.seconds, printB).get
    } finally {
      // close() may fail to join the read thread because UnixDomainSocket
      // input streams block until the server process exits (which happens
      // later in afterAll). Swallow the timeout.
      try session2.close()
      catch { case _: Exception => }
    }
  }
}
