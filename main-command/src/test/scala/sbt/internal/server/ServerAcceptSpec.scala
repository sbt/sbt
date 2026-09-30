/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.io.File
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import java.nio.file.{ Files, Paths }
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await
import scala.concurrent.duration.*

import sbt.internal.util.Util.isWindows
import sbt.protocol.ClientSocket
import verify.BasicTestSuite

object ServerAcceptSpec extends BasicTestSuite:
  private def withServer(
      onIncomingSocket: (AtomicReference[Socket], ServerInstance) => Unit
  )(f: (ServerInstance, File) => Unit): Unit =
    withConnection(onIncomingSocket)((instance, connection) => f(instance, connection.socketfile))

  private def shortTempDirectory(): java.nio.file.Path =
    if isWindows then Files.createTempDirectory("sbtsrv")
    else Files.createTempDirectory(Paths.get("/tmp"), "sbtsrv")

  private def withConnection(
      onIncomingSocket: (AtomicReference[Socket], ServerInstance) => Unit
  )(f: (ServerInstance, ServerConnection) => Unit): Unit =
    // the socket path has a length limit, so keep the directory short
    val dir = shortTempDirectory().toFile
    val connection = ServerConnection(
      connectionType = ConnectionType.Local,
      host = "127.0.0.1",
      port = 0,
      auth = Set.empty,
      portfile = new File(dir, "active.json"),
      tokenfile = new File(dir, "token.json"),
      socketfile = new File(dir, "sock"),
      pipeName = "sbt-test-" + dir.getName,
      appConfiguration = null, // only a bsp connection file reads it, and bsp is off here
      useJni = false,
      bspEnabled = false,
    )
    val instance = Server.start(connection, onIncomingSocket, sbt.util.Logger.Null)
    Await.ready(instance.ready, 10.seconds)
    try f(instance, connection)
    finally
      instance.shutdown()
      sbt.io.IO.delete(dir)
  end withConnection

  private def waitUntil(p: => Boolean): Boolean =
    val deadline = 10.seconds.fromNow
    while !p && deadline.hasTimeLeft() do Thread.sleep(20)
    p

  test("a client that the server fails to serve"):
    val served = new AtomicInteger
    val handler: (AtomicReference[Socket], ServerInstance) => Unit = (_, _) =>
      served.incrementAndGet()
      throw new RuntimeException("this client cannot be served")
    withServer(handler): (_, socketfile) =>
      ClientSocket.unixSocket(socketfile.toPath)
      assert(waitUntil(served.get == 1))
      ClientSocket.unixSocket(socketfile.toPath)
      // the loop accepted a second client, so the first one did not end it
      assert(waitUntil(served.get == 2))

  test("a socket the callback takes over"):
    val served = new AtomicInteger
    val first = new AtomicReference[Socket]
    val handler: (AtomicReference[Socket], ServerInstance) => Unit = (socket, _) =>
      if served.getAndIncrement == 0 then
        first.set(socket.get)
        AtomicCloseable.release(socket)
    withServer(handler): (_, socketfile) =>
      ClientSocket.unixSocket(socketfile.toPath)
      ClientSocket.unixSocket(socketfile.toPath)
      // the second client proves the loop went round, so it has passed its close
      assert(waitUntil(served.get == 2))
      assert(!first.get.isClosed)

  test("a socket the callback leaves"):
    val left = new AtomicReference[Socket]
    val handler: (AtomicReference[Socket], ServerInstance) => Unit =
      (socket, _) => left.set(socket.get)
    withServer(handler): (_, socketfile) =>
      ClientSocket.unixSocket(socketfile.toPath)
      assert(waitUntil(left.get ne null))
      assert(waitUntil(left.get.isClosed))

  test("a second server on the same socket"):
    withConnection((_, _) => ()): (_, connection) =>
      val second = Server.start(connection, (_, _) => (), sbt.util.Logger.Null)
      Await.ready(second.ready, 10.seconds)
      assert(
        second.ready.value.exists(
          _.failed.toOption.exists(_.isInstanceOf[AlreadyRunningException])
        )
      )

end ServerAcceptSpec
