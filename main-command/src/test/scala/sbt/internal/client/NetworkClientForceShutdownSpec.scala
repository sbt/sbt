/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package client

import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit, TimeoutException }

import sbt.internal.util.Util
import sbt.internal.util.Util.isWindows
import sbt.util.Level
import verify.BasicTestSuite

import FakeServer.*

/** `shutdown --force` against each [[NetworkClientForceShutdownSpec.ServerKind]]. */
object NetworkClientForceShutdownSpec extends BasicTestSuite:
  /**
   * The kind of server the fake server plays. It decides what `initialize` advertises and how `sbt/shutdown` is answered.
   */
  private enum ServerKind:
    /** Advertises the `shutdownRequest` capability and takes `sbt/shutdown`. */
    case Accepts

    /**
     * Advertises the capability but answers `sbt/shutdown` with an error. The client must print
     * the server's message and return 1, instead of waiting for a close that never comes.
     *
     * A real server refuses a connection that has not authenticated, and answers params it cannot
     * read with an internal error. `sbt --client` causes neither, because it sends the request
     * only after `initialize` succeeds and always sends `{"force":true}`, so this case covers
     * other clients and any refusal a later server adds.
     */
    case Refuses

    /**
     * A server released before `sbt/shutdown` existed, so it does not advertise the capability.
     * The client never sends `sbt/shutdown` to it: it warns that forced shutdown is not supported
     * and sends plain `shutdown` through `sbt/exec`. A server built from this version always advertises the capability,
     * so this one can only be faked.
     */
    case Legacy
  end ServerKind

  private def responder(server: ServerKind): Responder = (_, request) =>
    val id = stringFieldOrFail(request, "id")
    stringField(request, "method") match
      case Some("initialize") =>
        val capability = server != ServerKind.Legacy
        Reply.Answer(result(id, s"""{"capabilities":{"shutdownRequest":$capability}}"""))
      case Some("sbt/shutdown") if server == ServerKind.Refuses =>
        Reply.Answer(error(id, "'sbt/shutdown' is not allowed before authentication."))
      case Some("sbt/shutdown") =>
        Reply.AnswerAndClose(result(id, """{"status":"Processing"}"""))
      case Some("sbt/exec") =>
        Reply.AnswerAndClose(result(id, """{"status":"Done","exitCode":0}"""))
      case _ => Reply.Ignore

  private def runClient(client: NetworkClient, recorded: Recorded): Int =
    val exitCode = new LinkedBlockingQueue[Int]
    val thread = new Thread(() => Util.ignoreResult(exitCode.put(client.run())))
    thread.setDaemon(true)
    thread.start()
    Option(exitCode.poll(20, TimeUnit.SECONDS)).getOrElse:
      throw new TimeoutException(s"the client did not return; it sent ${recorded.requests}")

  private def forceShutdownAgainst(server: ServerKind)(f: (Int, Recorded) => Unit): Unit =
    withFakeServer(responder(server), Seq("shutdown", "--force")): (client, _, recorded) =>
      f(runClient(client, recorded), recorded)

  test("a server with sbt/shutdown is asked to force it"):
    if !isWindows then
      forceShutdownAgainst(ServerKind.Accepts): (exitCode, recorded) =>
        assert(exitCode == 0, recorded.logs.toString)
        val shutdown = recorded.request("sbt/shutdown")
        assert(
          shutdown.flatMap(booleanField(_, "params", "force")).contains(true),
          shutdown.toString
        )
        assert(!recorded.methods.contains("sbt/exec"), recorded.requests.toString)

  test("a server without sbt/shutdown gets plain shutdown and the user a warning"):
    if !isWindows then
      forceShutdownAgainst(ServerKind.Legacy): (exitCode, recorded) =>
        assert(exitCode == 0, recorded.logs.toString)
        assert(
          recorded.logged(Level.Warn).exists(_.contains("does not support forced shutdown")),
          recorded.logs.toString
        )
        assert(!recorded.methods.contains("sbt/shutdown"), recorded.requests.toString)
        val exec = recorded.request("sbt/exec")
        assert(
          exec.flatMap(stringField(_, "params", "commandLine")).contains("shutdown"),
          exec.toString
        )

  test("a refused forced shutdown fails with the reason the server gave"):
    if !isWindows then
      forceShutdownAgainst(ServerKind.Refuses): (exitCode, recorded) =>
        assert(exitCode == 1, recorded.logs.toString)
        assert(
          recorded.logged(Level.Error).exists(_.contains("not allowed before authentication")),
          recorded.logs.toString
        )
end NetworkClientForceShutdownSpec
