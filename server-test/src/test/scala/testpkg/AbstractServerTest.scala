/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ File, InputStream, PrintStream }
import java.nio.file.{ Files, Path }
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit, TimeoutException }
import scala.concurrent.duration.*
import sbt.internal.client.NetworkClient
import sbt.internal.util.Util
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
  def close(): Unit = {
    val result = scala.util.Try(session.shutdown(process.isAlive(), () => process.destroy()).get)
    if (process.isAlive()) process.destroy()
    result match {
      case scala.util.Failure(e) =>
        System.err.println(s"server session shutdown failed (process destroyed): $e")
      case _ =>
    }
  }
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
    ServerSession.waitForPortfile(portfile, process.isAlive())

    val session = ServerSession.connect(portfile)
    session.initialize(10.seconds, subscribeToAllForTest)

    svr = new SbtServer(session, buildDir, process)
  }

  private object BlockingInputStream extends InputStream {
    override def read(): Int = {
      try Thread.sleep(Long.MaxValue)
      catch { case _: InterruptedException => }
      -1
    }
  }
  private val nullPrintStream = new PrintStream(_ => {}, false)

  private def background[R](f: => R): R = {
    val result = new LinkedBlockingQueue[Either[Throwable, R]]
    val thread = new Thread("server-test-batch-client") {
      setDaemon(true)
      override def run(): Unit =
        try Util.ignoreResult(result.put(Right(f)))
        catch { case e: Throwable => Util.ignoreResult(result.put(Left(e))) }
    }
    thread.start()
    result.poll(3, TimeUnit.MINUTES) match {
      case null =>
        thread.interrupt()
        thread.join(10000)
        throw new TimeoutException("client did not complete within 3 minutes")
      case Left(e)  => throw e
      case Right(r) => r
    }
  }

  /** Runs the thin client in batch mode against this suite's server; returns its exit code. */
  protected def runBatchClient(args: String*): Int =
    background(
      NetworkClient.client(
        testPath.toFile,
        args.toArray,
        BlockingInputStream,
        nullPrintStream,
        nullPrintStream,
        false
      )
    )

  override protected def afterAll(): Unit = {
    svr.close()
    svr = null
    IO.delete(temp)
  }
}
