/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.{ File, InputStream, OutputStream, PrintStream }
import java.nio.file.{ Files, Path }
import scala.collection.mutable

import sbt.internal.client.NetworkClient
import sbt.io.IO
import sbt.io.syntax.*
import sbt.protocol.ServerSession
import sbt.{ ForkOptions, OutputStrategy, RunFromSourceMain }

import org.scalatest.funsuite.AnyFunSuite

/**
 * The thin client can only pass `-D` options to a server it starts itself, so a server
 * that is already running has to be restarted for them to take effect (sbt/sbt#9682).
 */
class ServerSysPropsTest extends AnyFunSuite {
  private val testDirectory = "client"
  private val sysPropsEnv = "SBT_SERVER_SYS_PROPS"
  private val sysPropsPortfileEnv = "SBT_SERVER_SYS_PROPS_PORTFILE"

  private val serverTestBase: File = {
    val p0 = new File(".").getAbsoluteFile / "server-test" / "src" / "server-test"
    val p1 = new File(".").getAbsoluteFile / "src" / "server-test"
    if (p0.exists) p0 else p1
  }

  private def portfile(buildDir: File): File =
    buildDir / "project" / "target" / "active.json"

  /** A script that stands in for the sbt script and never brings a server up. */
  private def deadScript(): String = {
    val f = Files.createTempFile("dead-sbt", ".sh")
    Files.writeString(f, "#!/usr/bin/env bash\necho dead-server >&2\nexit 1\n")
    f.toFile.setExecutable(true)
    f.toFile.deleteOnExit()
    f.toString
  }

  /**
   * Forks a server with the environment a client sets when it starts one. `optionsFor` is
   * the build the recorded options claim to belong to, which is this one unless a test says
   * otherwise, since the server only trusts options that name its own connection file.
   */
  private def withServer(
      sysProps: String,
      optionsFor: Option[File] = None
  )(f: (File, scala.sys.process.Process) => Unit): Unit = {
    val base: Path = Files.createTempDirectory("sbt-sysprops")
    val buildDir = base.toFile / testDirectory
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
        )
        .withEnvVars(
          Map(
            sysPropsEnv -> sysProps,
            sysPropsPortfileEnv -> portfile(optionsFor.getOrElse(buildDir)).getCanonicalPath,
          )
        ),
      buildDir,
      TestProperties.scalaVersion,
      TestProperties.version,
      classpath.toSeq
    )

    try {
      ServerSession.waitForPortfile(portfile(buildDir), process.isAlive())
      f(buildDir, process)
    } finally {
      if (process.isAlive()) process.destroy()
      IO.delete(base.toFile)
    }
  }

  private class CachingOutputStream extends OutputStream {
    private val bytes = new mutable.ArrayBuffer[Byte]
    override def write(i: Int): Unit = { bytes += i.toByte; () }
    def text: String = new String(bytes.toArray, "UTF-8")
  }

  /** Runs the thin client, returning its exit code and everything it logged. */
  private def client(baseDirectory: File, args: String*): (Int, String) = {
    val cos = new CachingOutputStream
    val out = new PrintStream(cos, true)
    try {
      val code = NetworkClient.client(
        baseDirectory,
        args.toArray,
        new InputStream { override def read(): Int = -1 },
        out,
        out,
        false
      )
      (code, cos.text)
    } finally
      // the client sets every -D option it parses on its own JVM, which here is the test JVM
      args.foreach { a =>
        if (a.startsWith("-D")) System.clearProperty(a.drop(2).takeWhile(_ != '='))
      }
  }

  private def exited(process: scala.sys.process.Process): Boolean = {
    val deadline = System.nanoTime + 60L * 1000000000L
    while (process.isAlive() && System.nanoTime < deadline) Thread.sleep(50)
    !process.isAlive()
  }

  test("the server records the -D options it was started with") {
    withServer("-Dmy.prop=first") { (buildDir, _) =>
      val recorded = IO.read(portfile(buildDir))
      assert(recorded.contains(""""sysProps":["-Dmy.prop=first"]"""), recorded)
    }
  }

  test("a client passing the same -D options keeps the running server") {
    withServer("-Dmy.prop=first") { (buildDir, process) =>
      val (code, log) = client(buildDir, "-Dmy.prop=first", "willSucceed")
      assert(code == 0, log)
      assert(process.isAlive(), s"the server was restarted even though nothing changed: $log")
    }
  }

  test("a completion query keeps the running server") {
    withServer("-Dmy.prop=first") { (buildDir, process) =>
      // completion args never carry the user's -D options, so they say nothing about
      // the server and pressing tab must not take it down
      val out = new PrintStream(new CachingOutputStream, true)
      NetworkClient.complete(
        buildDir,
        Array("--completions=sbtn comp"),
        false,
        new InputStream { override def read(): Int = -1 },
        out
      )
      assert(process.isAlive(), "a completion query shut the server down")
    }
  }

  test("options inherited from another build are not recorded") {
    // an sbt server passes its environment on to every sbt it starts itself
    val otherBuild = Files.createTempDirectory("sbt-other-build").toFile
    withServer("-Dmy.prop=first", optionsFor = Some(otherBuild)) { (buildDir, _) =>
      val recorded = IO.read(portfile(buildDir))
      assert(recorded.contains(""""sysProps":[]"""), recorded)
    }
  }

  test("a -D option written after the command keeps the running server") {
    withServer("-Dmy.prop=first") { (buildDir, process) =>
      // sbt parses it as part of the command, so the server is not missing anything
      val (_, log) = client(buildDir, "compile", "-Dmy.prop=second")
      assert(!log.contains("restarting it"), log)
      assert(process.isAlive(), s"the server was restarted over a command argument: $log")
    }
  }

  test("a bare exit keeps the running server") {
    withServer("-Dmy.prop=first") { (buildDir, process) =>
      // there is nothing to run, so a fresh server would be started only to say goodbye
      val (code, log) = client(buildDir, "-Dmy.prop=second", "exit")
      assert(code == 0, log)
      assert(process.isAlive(), s"the server was restarted for an exit: $log")
    }
  }

  test("a client passing changed -D options restarts the server") {
    withServer("-Dmy.prop=first") { (buildDir, process) =>
      // the client stops the running server and then fails to start a new one,
      // because the script it would start it with is a dead end
      val (_, log) =
        client(buildDir, s"--sbt-script=${deadScript()}", "-Dmy.prop=second", "compile")
      assert(log.contains("restarting it"), log)
      assert(exited(process), s"the server kept running with the options of an older client: $log")
    }
  }

  test("a client passing -D options restarts a server that recorded none") {
    // a server without any recorded options is running without them, so it is missing
    // the ones this client carries
    withServer("") { (buildDir, process) =>
      val (_, log) =
        client(buildDir, s"--sbt-script=${deadScript()}", "-Dmy.prop=first", "compile")
      assert(log.contains("restarting it"), log)
      assert(exited(process), s"the server kept running without the options it was passed: $log")
    }
  }

  test("a client that drops a -D option restarts the server") {
    withServer("-Dmy.prop=first") { (buildDir, process) =>
      val (_, log) = client(buildDir, s"--sbt-script=${deadScript()}", "compile")
      assert(log.contains("restarting it"), log)
      assert(exited(process), s"the server kept running with the options of an older client: $log")
    }
  }

  test("sbt.server.autorestart=false keeps the running server") {
    withServer("-Dmy.prop=first") { (buildDir, process) =>
      try {
        val (code, log) =
          client(buildDir, "-Dsbt.server.autorestart=false", "-Dmy.prop=second", "willSucceed")
        assert(code == 0, log)
        assert(process.isAlive(), s"the server was restarted with autorestart turned off: $log")
      } finally System.clearProperty("sbt.server.autorestart")
    }
  }
}
