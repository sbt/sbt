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

import java.io.{ ByteArrayInputStream, File, PrintStream }
import java.net.Socket
import java.nio.file.{ Files, Paths }
import java.util.concurrent.{ ConcurrentLinkedQueue, CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.Try

import sbt.internal.server.{ Server, ServerConnection, ServerInstance }
import sbt.internal.util.Util
import sbt.internal.util.Util.isWindows
import sbt.protocol.{ ClientSocket, JsonRpcReader, JsonRpcWriter }
import sbt.util.Level
import verify.BasicTestSuite

object ClientTokenRetrySpec extends BasicTestSuite:
  private def recording(handshakes: Handshakes) = new ConsoleInterface:
    override def appendLog(level: Level.Value, message: => String): Unit =
      if level == Level.Error then Util.ignoreResult(handshakes.errors.add(message))
    override def success(msg: String): Unit = ()

  private final class Handshakes:
    val presented = new ConcurrentLinkedQueue[String]
    val accepted = new ConcurrentLinkedQueue[String]
    val authenticated = new AtomicBoolean(false)
    val errors = new ConcurrentLinkedQueue[String]

  private def fieldOpt(name: String, json: String): Option[String] =
    s""""$name"\\s*:\\s*"([^"]+)"""".r.findFirstMatchIn(json).map(_.group(1))

  private def field(name: String, json: String): String =
    fieldOpt(name, json).getOrElse(sys.error(s"no $name in $json"))

  private def waitUntil(p: => Boolean): Boolean =
    val deadline = 3.seconds.fromNow
    while !p && deadline.hasTimeLeft() do Thread.sleep(20)
    p

  /*
   * Answers the first handshake the way a server that lost the token to another client
   * does: it spends the token itself, so the file names the next one, and refuses the
   * client that presented it. The second handshake it answers for real.
   */
  private def refusing(handshakes: Handshakes, every: Boolean)(
      socket: AtomicReference[Socket],
      instance: ServerInstance
  ): Unit =
    val client = socket.get
    val thread = new Thread("token-retry-spec-channel"):
      setDaemon(true)
      override def run(): Unit =
        val running = new AtomicBoolean(true)
        val in = client.getInputStream
        val out = client.getOutputStream
        while running.get do
          val request = Try(JsonRpcReader.readAsString(in, running)).getOrElse("")
          if request.isEmpty then running.set(false)
          else if fieldOpt("method", request).contains("sbt/exec") then
            val id = field("id", request)
            val body =
              if handshakes.authenticated.get then
                s"""{"jsonrpc":"2.0","id":"$id","result":{"exitCode":0}}"""
              else
                s"""{"jsonrpc":"2.0","id":"$id","error":{"code":-32600,""" +
                  s""""message":"'sbt/exec' is not allowed before authentication."}}"""
            Try(JsonRpcWriter.write(out, body)).failed.foreach(_ => running.set(false))
          else if !fieldOpt("method", request).contains("initialize") then ()
          else
            val id = field("id", request)
            val token = field("token", request)
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
            val body =
              if accept then s"""{"jsonrpc":"2.0","id":"$id","result":{}}"""
              else
                s"""{"jsonrpc":"2.0","id":"$id","error":{"code":-32600,"message":"invalid token"}}"""
            Try(JsonRpcWriter.write(out, body)).failed.foreach(_ => running.set(false))
          end if
        end while
      end run
    thread.start()
    AtomicCloseable.release(socket) // i took over
  end refusing

  private def withRefusingServer(every: Boolean = false)(
      f: (NetworkClient, File, Handshakes) => Unit
  ): Unit =
    // the socket path has a length limit, so keep the directory short
    val base = Files.createTempDirectory(Paths.get("/tmp"), "sbttok").toFile
    val portfile = new File(new File(new File(base, "project"), "target"), "active.json")
    sbt.io.IO.createDirectory(portfile.getParentFile)
    val handshakes = new Handshakes
    val connection = ServerConnection(
      connectionType = ConnectionType.Local,
      host = "127.0.0.1",
      port = 0,
      auth = Set(ServerAuthentication.Token),
      portfile = portfile,
      tokenfile = new File(base, "token.json"),
      socketfile = new File(base, "sock"),
      pipeName = "sbt-test-" + base.getName,
      appConfiguration = null, // only a bsp connection file reads it, and bsp is off here
      windowsServerSecurityLevel = 0,
      useJni = false,
      bspEnabled = false,
    )
    val instance = Server.start(connection, refusing(handshakes, every), sbt.util.Logger.Null)
    Await.ready(instance.ready, 10.seconds)
    val devNull = new PrintStream(java.io.OutputStream.nullOutputStream)
    val arguments = new NetworkClient.Arguments(base, Nil, Nil, Nil, "sbt", false, None)
    val client = new NetworkClient(
      arguments,
      recording(handshakes),
      new ByteArrayInputStream(Array.emptyByteArray),
      devNull,
      devNull,
      useJNI = false,
    )
    try f(client, portfile, handshakes)
    finally
      Util.ignoreTry(client.close())
      instance.shutdown()
      sbt.io.IO.delete(base)
  end withRefusingServer

  test("a token the server refuses"):
    if !isWindows then
      withRefusingServer(): (client, portfile, handshakes) =>
        val first = ClientSocket.token(portfile).get
        Util.ignoreTry(client.connection)
        assert(waitUntil(!handshakes.presented.isEmpty))
        // the token it presented was the one the file named, and it was refused anyway
        assert(handshakes.presented.peek == first)
        // the client never reads the token again, so the server never accepts it
        assert(!waitUntil(!handshakes.accepted.isEmpty))

  test("a command run while the first token is refused"):
    if !isWindows then
      withRefusingServer(): (client, _, _) =>
        Util.ignoreTry(client.connection)
        assert(client.batchExecute(List("compile")) == 1)

  test("two connections handshaking at once"):
    if !isWindows then
      withRefusingServer(): (client, _, handshakes) =>
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
        // a refused connection is never retried, so it stays unauthenticated
        assert(!waitUntil(handshakes.accepted.size == 2))
        threads.foreach(_.join(1000))

  test("a token the server always refuses"):
    if !isWindows then
      withRefusingServer(every = true): (client, _, handshakes) =>
        Util.ignoreTry(client.connection)
        assert(waitUntil(!handshakes.presented.isEmpty))
        // the client presents the one token and gives up without a word
        assert(!waitUntil(handshakes.presented.size > 1))
        assert(handshakes.errors.isEmpty, handshakes.errors.toString)
end ClientTokenRetrySpec
