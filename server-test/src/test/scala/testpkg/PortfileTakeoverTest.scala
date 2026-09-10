/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.File
import java.lang.ProcessBuilder.Redirect

import sbt.io.IO
import sbt.io.syntax.*

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Properties.isLinux

import org.scalatest.Outcome

/**
 * An sbt server that finds another one serving the build keeps the build loaded and serves
 * nothing. This covers what becomes of the portfile when a client deletes it while such a
 * server is sitting there.
 *
 * A case leaves the build with no portfile, or with one that names the sbt server it forked,
 * so each case gets a suite and a server of its own.
 */
trait AbstractTakeoverTest extends AbstractServerTest:
  override val testDirectory: String = "client"

  protected val settle: FiniteDuration = 90.seconds

  protected def portfile: File = svr.baseDirectory / "project" / "target" / "active.json"
  protected def marker: File = svr.baseDirectory / "loaded.txt"
  private def logfile: File = svr.baseDirectory / "another-server.log"

  /** The forked sbt server writes nowhere a failing run can reach, so hand its log over. */
  override protected def withFixture(test: NoArgTest): Outcome =
    val outcome = super.withFixture(test)
    if !outcome.isSucceeded && logfile.exists then
      System.err.println(IO.readLines(logfile).takeRight(40).mkString("\n"))
    outcome

  /**
   * Another sbt server on this build. It finds the socket taken, and then waits in the shell
   * with the build loaded.
   */
  protected def anotherServer(reloading: Boolean): Process =
    val java = new File(new File(System.getProperty("java.home"), "bin"), "java").toString
    val classpath = TestProperties.classpath
    val jvm = List(java, "-Djline.terminal=none", "-Dsbt.io.virtual=false", "-Dsbt.banner=false")
    val ivy = sys.props.get("sbt.ivy.home").map(h => s"-Dsbt.ivy.home=$h").toList
    val main = List("-cp", classpath, "sbt.RunFromSourceMain")
    val args = List(
      svr.baseDirectory.toString,
      TestProperties.scalaVersion,
      TestProperties.version,
      classpath,
      "startServer",
    ) ++ (if reloading then List("reload") else Nil) ++ List(
      "markLoaded",
      "shell",
    )
    val options = jvm ::: ivy ::: main ::: args
    val builder = new ProcessBuilder(options.asJava)
    /*
     * Inherited, this tells the child a thin client started it, and such an sbt server shuts
     * down as soon as it finds it serves nothing.
     */
    builder.environment.remove("SBT_TERMINAL_PROPS")
    IO.delete(marker)
    builder
      .directory(svr.baseDirectory)
      .redirectOutput(Redirect.to(logfile))
      .redirectErrorStream(true)
      .start()
  end anotherServer
end AbstractTakeoverTest

/* Linux alone: forking a second sbt server is expensive, and this behaviour is the same
 * everywhere. */
class PortfileTakeoverAfterLoadTest extends AbstractTakeoverTest:
  test("a portfile deleted while another sbt server is loaded") {
    if isLinux then
      val another = anotherServer(reloading = true)
      try
        assert(waitUntil(settle)(marker.exists), "another sbt server loads the build twice")
        val owner = IO.read(portfile)
        IO.delete(portfile)
        assert(!waitUntil(settle)(portfile.exists), "the portfile stays deleted")
      finally another.destroy()
  }
end PortfileTakeoverAfterLoadTest

/* Linux alone, for the same reason. */
class PortfileTakeoverTest extends AbstractTakeoverTest:
  test("a portfile deleted while another sbt server waits") {
    if isLinux then
      val another = anotherServer(reloading = false)
      try
        assert(waitUntil(settle)(marker.exists), "another sbt server loads the build")
        val owner = IO.read(portfile)
        IO.delete(portfile)
        assert(waitUntil(settle)(portfile.exists), "a portfile appears again")
        assert(IO.read(portfile) != owner, "it names the sbt server that was waiting")
      finally another.destroy()
  }
end PortfileTakeoverTest
