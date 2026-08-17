/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.client

import java.io.{ ByteArrayOutputStream, File, InputStream, PrintStream }
import java.net.{ ServerSocket, Socket }
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import verify.BasicTestSuite

/**
 * Regression test for the silent half of #9484: the server is up (its portfile
 * exists and accepts connections) but drops the client's connection during a
 * batch run. The client must say why it failed instead of exiting 1 silently.
 */
object NetworkClientSilentDisconnectTest extends BasicTestSuite:

  /** A server that accepts one connection, reads nothing, then closes it. */
  class DropServer:
    val serverSocket = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress)
    val accepted = new CountDownLatch(1)
    val thread = new Thread(() =>
      try
        val s: Socket = serverSocket.accept()
        accepted.countDown()
        Thread.sleep(200) // let the client write its handshake into the kernel buffer
        s.close()
      catch case _: Exception => ()
    )
    thread.setDaemon(true)
    thread.start()
    def port: Int = serverSocket.getLocalPort
    def close(): Unit =
      try serverSocket.close()
      catch case _: Exception => ()

  def withDropServer[A](f: DropServer => A): A =
    val server = new DropServer
    try f(server)
    finally server.close()

  def projectWithPortfile(port: Int): File =
    val base = Files.createTempDirectory("silent-disconnect-project").toFile
    Files.writeString(
      base.toPath.resolve("build.sbt"),
      "lazy val root = (project in file(\".\"))\n"
    )
    val target = base.toPath.resolve("project").resolve("target")
    Files.createDirectories(target)
    Files.writeString(
      target.resolve("active.json"),
      s"""{"uri":"tcp://127.0.0.1:$port","tokenfilePath":null,"tokenfileUri":null}"""
    )
    base

  test("a dropped connection during a batch run must not exit silently"):
    withDropServer: server =>
      val base = projectWithPortfile(server.port)
      val (code, explained) = runBatchClient(base)
      assert(code == 1, s"expected exit 1, got $code")
      assert(
        explained.contains("disconnected"),
        s"client exited 1 without explaining the disconnect: '$explained'"
      )

  test("a corrupt portfile is replaced and a fresh server connection is attempted"):
    val base = Files.createTempDirectory("corrupt-portfile-project").toFile
    Files.writeString(
      base.toPath.resolve("build.sbt"),
      "lazy val root = (project in file(\".\"))\n"
    )
    val target = base.toPath.resolve("project").resolve("target")
    Files.createDirectories(target)
    val portfile = target.resolve("active.json")
    Files.writeString(portfile, "not json")
    try
      val (_, explained) = runBatchClient(base)
      assert(
        explained.contains("corrupt or unreadable") &&
          explained.contains("active.json") &&
          explained.contains("starting a new server"),
        s"client did not explain the corrupt portfile before retrying: '$explained'"
      )
      assert(
        !Files.exists(portfile) || Files.readString(portfile) != "not json",
        "corrupt portfile should have been replaced by a fresh server connection"
      )
    finally shutdownServer(base)

  test("an empty portfile is replaced and a fresh server connection is attempted"):
    val base = Files.createTempDirectory("empty-portfile-project").toFile
    Files.writeString(
      base.toPath.resolve("build.sbt"),
      "lazy val root = (project in file(\".\"))\n"
    )
    val target = base.toPath.resolve("project").resolve("target")
    Files.createDirectories(target)
    val portfile = target.resolve("active.json")
    Files.writeString(portfile, "")
    try
      val (_, explained) = runBatchClient(base)
      assert(
        explained.contains("corrupt or unreadable") &&
          explained.contains("active.json") &&
          explained.contains("starting a new server"),
        s"client did not explain the corrupt portfile before retrying: '$explained'"
      )
      assert(
        !Files.exists(portfile) || Files.readString(portfile).nonEmpty,
        "empty portfile should have been replaced by a fresh server connection"
      )
    finally shutdownServer(base)

  private def shutdownServer(base: File): Unit =
    try runBatchClient(base, Array("shutdown"))
    catch case _: Exception => ()

  private def runBatchClient(base: File, args: Array[String] = Array("compile")): (Int, String) =
    val errBytes = new ByteArrayOutputStream
    val outBytes = new ByteArrayOutputStream
    val err = new PrintStream(errBytes, true)
    val out = new PrintStream(outBytes, true)
    val code = NetworkClient.client(
      base,
      args,
      new InputStream { override def read(): Int = -1 },
      out,
      err,
      false,
    )
    (code, errBytes.toString("UTF-8") + outBytes.toString("UTF-8"))
end NetworkClientSilentDisconnectTest
