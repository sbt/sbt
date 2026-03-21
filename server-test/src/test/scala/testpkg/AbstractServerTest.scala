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
import sbt.io.IO
import sbt.io.syntax.*
import sbt.protocol.ServerSession
import sbt.{ ForkOptions, OutputStrategy, RunFromSourceMain }

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll

/**
 * Holds the running sbt server instance: the session for JSON-RPC communication,
 * the base directory of the test build, and the forked process handle.
 */
final class SbtServer(
    val session: ServerSession,
    val baseDirectory: File,
    private val process: scala.sys.process.Process
) {
  def close(): Unit =
    session.shutdown(process.isAlive, () => process.destroy()).get
}

trait AbstractServerTest extends AnyFunSuite with BeforeAndAfterAll {
  private var temp: File = scala.compiletime.uninitialized
  var svr: SbtServer = scala.compiletime.uninitialized

  def testDirectory: String
  def testPath: Path = temp.toPath.resolve(testDirectory)
  def subscribeToAllForTest: Boolean = true

  private val serverTestBase: File = {
    val p0 = new File(".").getAbsoluteFile / "server-test" / "src" / "server-test"
    val p1 = new File(".").getAbsoluteFile / "src" / "server-test"
    if (p0.exists) p0
    else p1
  }

  private val targetDir: File = {
    val p0 = new File("..").getAbsoluteFile.getCanonicalFile / "target"
    val p1 = new File("target").getAbsoluteFile
    if (p0.exists) p0
    else p1
  }

  override def beforeAll(): Unit = {
    val base = Files.createTempDirectory(
      Files.createDirectories(targetDir.toPath.resolve("test-server")),
      "server-test"
    )
    temp = base.toFile
    val buildDir = temp / testDirectory
    IO.copyDirectory(serverTestBase / testDirectory, buildDir)

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

    val portfile = buildDir / "project" / "target" / "active.json"
    ServerSession.waitForPortfile(portfile, process.isAlive)

    val session = ServerSession.connect(portfile)
    session.initialize(10.seconds, subscribeToAllForTest)

    svr = new SbtServer(session, buildDir, process)
  }

  override protected def afterAll(): Unit = {
    svr.close()
    svr = null
    IO.delete(temp)
  }
}
