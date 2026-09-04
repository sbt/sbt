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
import java.nio.file.{ Files, Paths }

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.Try

import sbt.internal.server.{ Server, ServerConnection }
import sbt.internal.util.Util.isWindows
import sbt.util.Level
import verify.BasicTestSuite

object ClientConnectSpec extends BasicTestSuite:
  private val quiet = new ConsoleInterface:
    override def appendLog(level: Level.Value, message: => String): Unit = ()
    override def success(msg: String): Unit = ()

  private def withServerAndClient(f: (NetworkClient, File) => Unit): Unit =
    // the socket path has a length limit, so keep the directory short
    val base = Files.createTempDirectory(Paths.get("/tmp"), "sbtcli").toFile
    val portfile = new File(new File(new File(base, "project"), "target"), "active.json")
    sbt.io.IO.createDirectory(portfile.getParentFile)
    val connection = ServerConnection(
      connectionType = ConnectionType.Local,
      host = "127.0.0.1",
      port = 0,
      auth = Set.empty,
      portfile = portfile,
      tokenfile = new File(base, "token.json"),
      socketfile = new File(base, "sock"),
      pipeName = "sbt-test-" + base.getName,
      appConfiguration = null, // only a bsp connection file reads it, and bsp is off here
      windowsServerSecurityLevel = 0,
      useJni = false,
      bspEnabled = false,
    )
    val instance = Server.start(connection, (_, _) => (), sbt.util.Logger.Null)
    Await.ready(instance.ready, 10.seconds)
    val devNull = new PrintStream(java.io.OutputStream.nullOutputStream)
    val arguments = new NetworkClient.Arguments(base, Nil, Nil, Nil, "sbt", false, None)
    val client = new NetworkClient(
      arguments,
      quiet,
      new ByteArrayInputStream(Array.emptyByteArray),
      devNull,
      devNull,
      useJNI = false,
    )
    try f(client, portfile)
    finally
      Try(client.close())
      instance.shutdown()
      sbt.io.IO.delete(base)

  test("a portfile that a server is still writing"):
    if !isWindows then
      withServerAndClient: (client, portfile) =>
        val published = sbt.io.IO.read(portfile)
        sbt.io.IO.write(portfile, published.take(published.length / 2))
        val restore = new Thread(() =>
          Thread.sleep(10)
          sbt.io.IO.write(portfile, published)
        )
        restore.setDaemon(true)
        restore.start()
        def connect() = client.connectOrStartServerAndConnect(false, retry = false)
        val connected = Try(connect())
        restore.join()
        // the client reads again, so it meets the whole portfile and reaches the server
        assert(connected.isSuccess)
        connected.foreach((socket, _) => socket.close())
end ClientConnectSpec
