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

import java.io.File
import java.util.concurrent.{ ConcurrentLinkedQueue, CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean
import sbt.internal.util.Util
import sbt.internal.util.Util.isWindows
import sbt.protocol.ClientSocket
import sbt.util.Level
import verify.BasicTestSuite
import FakeServer.*

import scala.concurrent.duration.DurationInt

object ClientTokenRetrySpec extends BasicTestSuite:
  private final class Handshakes:
    val presented = new ConcurrentLinkedQueue[String]
    val accepted = new ConcurrentLinkedQueue[String]
    val authenticated = new AtomicBoolean(false)

  private def waitUntil(p: => Boolean): Boolean =
    val deadline = 3.seconds.fromNow
    while !p && deadline.hasTimeLeft() do Thread.sleep(20)
    p

  /*
   * Answers the first handshake the way a server that lost the token to another client
   * does: it spends the token itself, so the file names the next one, and refuses the
   * client that presented it. The second handshake it answers for real.
   */
  private def refusing(handshakes: Handshakes, every: Boolean): Responder =
    (instance, request) =>
      stringField(request, "method") match
        case Some("sbt/exec") =>
          val id = stringFieldOrFail(request, "id")
          if handshakes.authenticated.get then Reply.Answer(result(id, """{"exitCode":0}"""))
          else Reply.Answer(error(id, "'sbt/exec' is not allowed before authentication."))
        case Some("initialize") =>
          val id = stringFieldOrFail(request, "id")
          val token = stringFieldOrFail(request, "params", "initializationOptions", "token")
          val accept =
            if every then false
            else if handshakes.presented.isEmpty then
              // another client spends it first, which rotates the file to the next token
              Util.ignoreResult(instance.authenticate(token))
              // a server answers over a socket, so a request sent meanwhile arrives first
              Thread.sleep(200)
              false
            else instance.authenticate(token)
          handshakes.presented.add(token)
          if accept then
            handshakes.accepted.add(token)
            handshakes.authenticated.set(true)
            Reply.Answer(result(id, "{}"))
          else Reply.Answer(error(id, "invalid token"))
        case _ => Reply.Ignore

  private def withRefusingServer(every: Boolean = false)(
      f: (NetworkClient, File, Handshakes, Recorded) => Unit
  ): Unit =
    val handshakes = new Handshakes
    withFakeServer(refusing(handshakes, every)): (client, portfile, recorded) =>
      f(client, portfile, handshakes, recorded)

  test("a token the server refuses"):
    if !isWindows then
      withRefusingServer(): (client, portfile, handshakes, _) =>
        val first = ClientSocket.token(portfile).get
        Util.ignoreTry(client.connection)
        assert(waitUntil(!handshakes.presented.isEmpty))
        // the token it presented was the one the file named, and it was refused anyway
        assert(handshakes.presented.peek == first)
        // the client reads the token again, so the server accepts it on the second try
        assert(waitUntil(!handshakes.accepted.isEmpty))

  test("a command run while the first token is refused"):
    if !isWindows then
      withRefusingServer(): (client, _, _, _) =>
        Util.ignoreTry(client.connection)
        assert(client.batchExecute(List("compile")) == 0)

  test("two connections handshaking at once"):
    if !isWindows then
      withRefusingServer(): (client, _, handshakes, _) =>
        val done = new CountDownLatch(2)
        def connect(): Thread =
          val t = new Thread(() =>
            Util.ignoreTry(client.init(promptCompleteUsers = false, retry = false))
            done.countDown()
          )
          t.setDaemon(true)
          t.start()
          t
        val threads = List(connect(), connect())
        assert(done.await(20, TimeUnit.SECONDS), handshakes.presented.toString)
        // the first refusal is held back, so the second handshake is sent inside that window
        assert(waitUntil(handshakes.presented.size >= 2), handshakes.presented.toString)
        // each connection retries its own refusal, so both end up authenticated
        assert(waitUntil(handshakes.accepted.size == 2))
        threads.foreach(_.join(1000))

  test("a token the server always refuses"):
    if !isWindows then
      withRefusingServer(every = true): (client, _, handshakes, recorded) =>
        Util.ignoreTry(client.connection)
        // the client presents a token once per attempt, then reports the refusal
        assert(waitUntil(handshakes.presented.size == NetworkClient.handshakeAttemptLimit))
        assert(!waitUntil(handshakes.presented.size > NetworkClient.handshakeAttemptLimit))
        assert(recorded.logged(Level.Error).exists(_.contains("refused the connection")))
end ClientTokenRetrySpec
