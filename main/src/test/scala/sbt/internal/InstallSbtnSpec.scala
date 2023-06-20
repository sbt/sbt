/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.{ InputStream, OutputStream, PrintStream }
import java.lang.ProcessBuilder
import java.lang.ProcessBuilder.Redirect
import java.nio.file.{ Files, Path }
import java.util.concurrent.TimeUnit
import org.scalatest.flatspec.AnyFlatSpec
import sbt.io.IO

class InstallSbtnSpec extends AnyFlatSpec {
  private def withTemp[R](ext: String)(f: Path => R): R = {
    val tmp = Files.createTempFile("sbt-1.4.1-", ext)
    try f(tmp)
    finally {
      Files.deleteIfExists(tmp)
      ()
    }
  }
  private[this] val term = new Terminal {
    def getHeight: Int = 0
    def getWidth: Int = 0
    def inputStream: InputStream = () => -1
    def printStream: PrintStream = new PrintStream((_ => {}): OutputStream)
    def setMode(canonical: Boolean, echo: Boolean): Unit = {}

  }
  // This test has issues in ci but runs ok locally on all platforms
  "InstallSbtn" should "extract native sbtn" ignore
    withTemp(".zip") { tmp =>
      withTemp(".exe") { sbtn =>
        InstallSbtn.extractSbtn(term, "1.9.0", tmp, sbtn)
        val tmpDir = Files.createTempDirectory("sbtn-test").toRealPath()
        Files.createDirectories(tmpDir.resolve("project"))
        val foo = tmpDir.resolve("foo")
        val fooPath = foo.toString.replace("\\", "\\\\")
        val build = s"""TaskKey[Unit]("foo") := IO.write(file("$fooPath"), "foo")"""
        IO.write(tmpDir.resolve("build.sbt").toFile, build)
        IO.write(
          tmpDir.resolve("project").resolve("build.properties").toFile,
          "sbt.version=1.9.0"
        )
        try {
          val proc =
            new ProcessBuilder(sbtn.toString, "foo;shutdown")
              .redirectInput(Redirect.INHERIT)
              .redirectOutput(Redirect.INHERIT)
              .redirectError(Redirect.INHERIT)
              .directory(tmpDir.toFile)
              .start()
          proc.waitFor(1, TimeUnit.MINUTES)
          assert(proc.exitValue == 0)
          assert(IO.read(foo.toFile) == "foo")
        } finally {
          sbt.io.IO.delete(tmpDir.toFile)
        }
      }
    }
}
