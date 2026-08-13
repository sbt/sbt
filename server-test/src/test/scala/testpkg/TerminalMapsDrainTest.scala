/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import sbt.internal.langserver.SbtExecParams
import sbt.internal.util.Util
import sbt.protocol.{ Attach, Serialization, SilentTerminalSession }
import sbt.protocol.codec.JsonProtocol.given
import sbt.internal.langserver.codec.JsonProtocol.given

/**
 * Regression: a client that dies while the server waits on a terminal control answer
 * must not strand the parked server-side thread.
 */
class TerminalMapsDrainTest extends AbstractServerTest {
  override val testDirectory: String = "client"

  test("a client dying at a failed-reload prompt does not strand the server") {
    val portfile = new java.io.File(testPath.toFile, "project/target/active.json")
    val buildFile = testPath.resolve("build.sbt")
    val goodBuild = java.nio.file.Files.readString(buildFile)
    val silent = SilentTerminalSession.connect(portfile)
    var parkedOn = "none"
    try {
      silent.initialize(10.seconds, false).get
      Util.ignoreResult(
        silent.sendJsonRpc(silent.nextId(), Serialization.attach, Attach(interactive = true))
      )
      java.nio.file.Files.writeString(buildFile, "val = =\n")
      silent.sendJsonRpc(silent.nextId(), "sbt/exec", SbtExecParams("reload")).get
      val queried = silent.silentQuery.await(60, TimeUnit.SECONDS)
      val input = silent.inputRequested.await(5, TimeUnit.SECONDS)
      assert(queried || input, "server never queried the terminal nor requested input")
      // the command loop is now parked waiting for an answer that never comes
      parkedOn =
        Option(silent.firstSilentQuery.get).getOrElse(if (input) "readSystemIn" else "unknown")
    } finally {
      silent.close()
      java.nio.file.Files.writeString(buildFile, goodBuild)
    }
    // EOF at the failed-load prompt maps to 'q' by design: the server must shut down
    // cleanly rather than stay parked on the dead client's unanswered query.
    def serverAlive: Boolean =
      ProcessHandle.current.descendants.anyMatch { ph =>
        ph.info.command.orElse("").contains("java")
      }
    val deadline = 90.seconds.fromNow
    while (serverAlive && deadline.hasTimeLeft()) Thread.sleep(500)
    assert(!serverAlive, s"server must exit after the prompting client dies (parked on $parkedOn)")
  }
}
