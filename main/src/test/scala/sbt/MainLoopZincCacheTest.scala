/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File

import sbt.internal.util.{ ConsoleOut, GlobalLogging, MainAppender }
import sbt.io.IO
import sbt.io.syntax.*
import sbt.librarymanagement.SbtArtifacts
import sbt.util.Logger

object MainLoopZincCacheTest extends verify.BasicTestSuite:

  private def withTestLog[A](f: Logger => A): A =
    val logFile = File.createTempFile("sbt-mlz", ".log")
    try
      val gl = GlobalLogging.initial(
        MainAppender.globalDefault(ConsoleOut.globalProxy),
        logFile,
        ConsoleOut.globalProxy
      )
      f(gl.full)
    finally IO.delete(logFile)

  test("deleteZincBridgeSecondaryCache removes org.scala-sbt under zincDir"):
    IO.withTemporaryDirectory: tmp =>
      val zincRoot = tmp / "zinc"
      val bridge = zincRoot / SbtArtifacts.Organization
      IO.write(bridge / "marker.txt", "cached")
      withTestLog: log =>
        MainLoop.deleteZincBridgeSecondaryCache(log, zincRoot)
      assert(!bridge.exists(), s"expected $bridge deleted")

  test("deleteZincBridgeSecondaryCache is a no-op when org.scala-sbt is absent"):
    IO.withTemporaryDirectory: tmp =>
      val zincRoot = tmp / "zinc"
      IO.createDirectory(zincRoot)
      withTestLog: log =>
        MainLoop.deleteZincBridgeSecondaryCache(log, zincRoot)
      assert(zincRoot.exists())

end MainLoopZincCacheTest
