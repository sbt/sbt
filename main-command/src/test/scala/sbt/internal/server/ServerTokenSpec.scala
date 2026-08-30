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
import java.nio.file.{ Files, Paths }
import java.nio.file.attribute.PosixFilePermission.{ OWNER_READ, OWNER_WRITE }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Try

import sjsonnew.support.scalajson.unsafe.{ Converter, Parser }
import sbt.internal.protocol.TokenFile
import sbt.internal.util.Util.isWindows
import verify.BasicTestSuite

object ServerTokenSpec extends BasicTestSuite:
  private def withServer(f: (ServerInstance, File) => Unit): Unit =
    // the socket path has a length limit, so keep the directory short
    val dir = Files.createTempDirectory(Paths.get("/tmp"), "sbttok").toFile
    val connection = ServerConnection(
      connectionType = ConnectionType.Local,
      host = "127.0.0.1",
      port = 0,
      auth = Set(ServerAuthentication.Token),
      portfile = new File(dir, "active.json"),
      tokenfile = new File(dir, "token.json"),
      socketfile = new File(dir, "sock"),
      pipeName = "sbt-test-" + dir.getName,
      appConfiguration = null, // only a bsp connection file reads it, and bsp is off here
      windowsServerSecurityLevel = 0,
      useJni = false,
      bspEnabled = false,
    )
    val instance = Server.start(connection, (_, _) => (), sbt.util.Logger.Null)
    Await.ready(instance.ready, 10.seconds)
    try f(instance, connection.tokenfile)
    finally
      instance.shutdown()
      sbt.io.IO.delete(dir)

  private def tokenIn(tokenfile: File): String =
    import Server.JsonProtocol.given
    val json = Parser.parseFromString(sbt.io.IO.read(tokenfile)).get
    Converter.fromJson[TokenFile](json).get.token

  test("the permission on a token file"):
    if !isWindows then
      withServer: (_, tokenfile) =>
        val permissions = Files.getPosixFilePermissions(tokenfile.toPath).asScala.toSet
        assert(permissions == Set(OWNER_READ, OWNER_WRITE))

  test("a client reading the token file while the server rotates it"):
    if !isWindows then
      withServer: (instance, tokenfile) =>
        val broken = new AtomicInteger
        val stop = new AtomicBoolean
        val reader = new Thread(() =>
          while !stop.get do if Try(tokenIn(tokenfile)).isFailure then broken.incrementAndGet()
        )
        reader.setDaemon(true)
        reader.start()
        var rotations = 0
        while rotations < 200 do
          val _ = instance.authenticate(tokenIn(tokenfile))
          rotations += 1
        stop.set(true)
        reader.join()
        // the rename puts the whole file in place at once, so a reader sees all of it
        assert(broken.get == 0)
end ServerTokenSpec
