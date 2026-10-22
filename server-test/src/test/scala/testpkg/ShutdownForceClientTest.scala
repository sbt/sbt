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
import sbt.internal.langserver.{ ErrorCodes, LogMessageParams, SbtExecParams }
import sbt.internal.langserver.codec.JsonProtocol.given

/**
 * The thin client's `shutdown --force` against a real server: the request it sends is one the
 * server decodes as forced, so the server cancels the running command and exits.
 */
class ShutdownForceClientTest extends AbstractServerTest:
  override val testDirectory: String = "shutdown"

  test("shutdown --force from the client cuts the running command short") {
    val slowId = svr.session.nextId()
    svr.session.sendJsonRpc(slowId, "sbt/exec", SbtExecParams("gatedSlowTask")).get
    svr.session
      .waitForParamsInNotificationMsg[LogMessageParams](5.seconds) { p =>
        p.message.contains("gatedSlowTask started")
      }
      .get

    assert(runBatchClient("shutdown", "--force") == 0, "the client did not exit 0")

    val slowResponse = svr.session.waitForResponseMsg(5.seconds, slowId).get
    assert(
      slowResponse.error.exists: e =>
        e.code == ErrorCodes.UnknownError && e.message == "gatedSlowTask",
      slowResponse.toString
    )
    assert((svr.baseDirectory / "interrupted").exists, "gatedSlowTask was not interrupted")
    assert(waitUntil(60.seconds)(!svr.isAlive), "the server did not exit after the shutdown")
  }
end ShutdownForceClientTest
