/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.File
import java.nio.file.{ Files, Path }
import scala.concurrent.duration.*

import sbt.internal.langserver.{ CancelRequestParams, ErrorCodes, SbtExecParams }
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.protocol.{ Attach, CompletionParams, SettingQuery }
import sbt.protocol.codec.JsonProtocol.given
import sbt.io.IO
import sbt.io.syntax.*
import sbt.protocol.ServerSession
import sbt.{ ForkOptions, OutputStrategy, RunFromSourceMain }
import sjsonnew.JsonWriter

import org.scalatest.funsuite.AnyFunSuite

/**
 * Reproduces the reported vulnerability: a TCP server configured with token auth
 * (the default whenever `serverConnectionType` is Tcp) must reject requests that
 * mutate or read through the server from a client that never completed a
 * token-authenticated `initialize`. Unlike the other tests in this suite, these
 * deliberately skip `ServerSession#initialize` to play the part of an attacker who
 * can reach the socket but does not know the token.
 */
class ExecRequiresInitializeTest extends AnyFunSuite {
  private val testDirectory = "tcp"

  private val serverTestBase: File = {
    val p0 = new File(".").getAbsoluteFile / "server-test" / "src" / "server-test"
    val p1 = new File(".").getAbsoluteFile / "src" / "server-test"
    if (p0.exists) p0 else p1
  }

  /** Forks a real sbt server for `testDirectory`, connects a raw (un-initialized) session. */
  private def withUnauthenticatedSession(f: ServerSession => Unit): Unit = {
    val base: Path = Files.createTempDirectory(Path.of("/tmp"), "sbt-tcp-poc")
    val buildDir = base.toFile / testDirectory
    IO.copyDirectory(serverTestBase / testDirectory, buildDir)
    info(s"test project created at: $buildDir")

    val classpath = TestProperties.classpath.split(File.pathSeparator).map(new File(_))
    val process = RunFromSourceMain.fork(
      ForkOptions()
        .withOutputStrategy(OutputStrategy.StdoutOutput)
        .withRunJVMOptions(
          Vector(
            "-Djline.terminal=none",
            "-Dsbt.io.virtual=false",
            "-Dsbt.banner=false",
          )
        ),
      buildDir,
      TestProperties.scalaVersion,
      TestProperties.version,
      classpath.toSeq
    )

    try {
      val portfile = buildDir / "project" / "target" / "active.json"
      ServerSession.waitForPortfile(portfile, process.isAlive())

      val session = ServerSession.connect(portfile)
      try
        // Deliberately do NOT call session.initialize(...): this simulates an
        // attacker who can reach the TCP socket but never authenticates.
        f(session)
      finally session.close()
    } finally {
      if (process.isAlive()) process.destroy()
      IO.delete(base.toFile)
    }
  }

  /** Sends `method`/`params` on `session` and asserts the server rejected it pre-auth. */
  private def assertRejected[A: JsonWriter](
      session: ServerSession,
      method: String,
      params: A
  ): Unit = {
    val id = session.nextId()
    session.sendJsonRpc(id, method, params).get
    val response = session.waitForResponseMsg(30.seconds, id).get

    assert(
      response.error.isDefined,
      s"$method should have been rejected before initialize, but got: $response"
    )
    assertResult(ErrorCodes.InvalidRequest)(response.error.get.code)
  }

  test("sbt/exec is rejected over TCP before a token-authenticated initialize") {
    withUnauthenticatedSession { session =>
      assertRejected(session, "sbt/exec", SbtExecParams("compile"))
    }
  }

  test("sbt/setting is rejected over TCP before a token-authenticated initialize") {
    withUnauthenticatedSession { session =>
      assertRejected(session, "sbt/setting", SettingQuery("root/name"))
    }
  }

  test("sbt/cancelRequest is rejected over TCP before a token-authenticated initialize") {
    withUnauthenticatedSession { session =>
      assertRejected(session, "sbt/cancelRequest", CancelRequestParams("some-id"))
    }
  }

  test("sbt/completion is rejected over TCP before a token-authenticated initialize") {
    withUnauthenticatedSession { session =>
      assertRejected(session, "sbt/completion", CompletionParams("comp", None))
    }
  }

  test("sbt/attach is rejected over TCP before a token-authenticated initialize") {
    // Regression for the interactive-attach bypass: without this gate, a client could
    // attach and feed raw command bytes via sbt/systemIn, running commands without
    // ever completing the token handshake that sbt/exec itself requires.
    withUnauthenticatedSession { session =>
      assertRejected(session, "sbt/attach", Attach(interactive = true))
    }
  }
}
