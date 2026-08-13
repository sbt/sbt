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
import java.util.concurrent.TimeUnit
import sbt.internal.client.NetworkClient
import sbt.internal.util.Util

/** Runs the thin client against a fake sbt script; exits with the client's exit code. */
object ClientBootTimeoutMain {
  def main(args: Array[String]): Unit = {
    val Array(base, script) = args
    val code = NetworkClient.client(
      new File(base),
      Array(s"--sbt-script=$script", "willSucceed"),
      new InputStream { override def read(): Int = -1 },
      new PrintStream(OutputStream.nullOutputStream),
      new PrintStream(System.err, true),
      false
    )
    System.exit(code)
  }
}

/**
 * A forked server that never writes its portfile must not hang the client forever:
 * the connect wait is bounded, and the failure explains itself. The client runs in a
 * forked JVM because the timeout is configured via the SBT_CLIENT_CONNECT_TIMEOUT
 * environment variable, which cannot be set in-process.
 */
class ClientBootTimeoutTest extends AbstractServerTest {
  override val testDirectory: String = "client"

  private def fakeServer(script: String): String = {
    val f = Files.createTempFile("fake-sbt", ".sh")
    Files.writeString(f, script)
    f.toFile.setExecutable(true)
    f.toString
  }

  test("a forked server that never starts fails within the connect timeout") {
    val base = Files.createTempDirectory("connect-timeout-project").toFile
    Files.writeString(
      base.toPath.resolve("build.sbt"),
      "lazy val root = (project in file(\".\"))\n"
    )
    val script = fakeServer("#!/usr/bin/env bash\necho fake-server-wedged >&2\nsleep 600\n")
    val errFile = Files.createTempFile("client-err", ".log")
    val javaBin = Path.of(sys.props("java.home"), "bin", if (Util.isWindows) "java.exe" else "java")
    val testClasses = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI)
    val pb = new ProcessBuilder(
      javaBin.toString,
      "-cp",
      TestProperties.classpath + File.pathSeparator + testClasses,
      "testpkg.ClientBootTimeoutMain",
      base.toString,
      script
    )
    pb.environment().put("SBT_CLIENT_CONNECT_TIMEOUT", "3")
    pb.redirectError(errFile.toFile)
    val started = System.nanoTime()
    val p = pb.start()
    val finished = p.waitFor(60, TimeUnit.SECONDS)
    val elapsed = (System.nanoTime() - started) / 1000000000L
    if (!finished) p.destroyForcibly()
    assert(finished, "client is still hanging after 60 seconds")
    assert(p.exitValue != 0, s"expected failure, got ${p.exitValue}")
    assert(elapsed < 45, s"connect wait was not bounded by the timeout (${elapsed}s)")
    val errText = Files.readString(errFile)
    assert(
      errText.contains("did not start within 3 seconds"),
      s"missing timeout message: $errText"
    )
    assert(errText.contains("fake-server-wedged"), s"server stderr not forwarded: $errText")
  }
}
