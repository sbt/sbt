/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import buildinfo.TestBuildInfo
import sbt.internal.scriptedtest.ScriptedLauncher
import sbt.util.LogExchange

import scala.annotation.tailrec
import scala.sys.process.Process

object RunFromSourceMain {
  private val sbtVersion = TestBuildInfo.version
  private val scalaVersion = "2.12.10"

  def fork(workingDirectory: File): Process = {
    val fo = ForkOptions()
      .withOutputStrategy(OutputStrategy.StdoutOutput)
    fork(fo, workingDirectory)
  }

  def fork(fo0: ForkOptions, workingDirectory: File): Process = {
    val fo = fo0
      .withWorkingDirectory(workingDirectory)
      .withRunJVMOptions(sys.props.get("sbt.ivy.home") match {
        case Some(home) => Vector(s"-Dsbt.ivy.home=$home")
        case _          => Vector()
      })
    implicit val runner = new ForkRun(fo)
    val cp = {
      TestBuildInfo.test_classDirectory +: TestBuildInfo.fullClasspath
    }
    val options = Vector(workingDirectory.toString)
    val log = LogExchange.logger("RunFromSourceMain.fork", None, None)
    runner.fork("sbt.RunFromSourceMain", cp, options, log)
  }

  def main(args: Array[String]): Unit = args match {
    case Array()              => sys.error(s"Must specify working directory as the first argument")
    case Array(wd, args @ _*) => run(file(wd), args)
  }

  // this arrangement is because Scala does not always properly optimize away
  // the tail recursion in a catch statement
  @tailrec private[sbt] def run(baseDir: File, args: Seq[String]): Unit =
    runImpl(baseDir, args) match {
      case Some((baseDir, args)) => run(baseDir, args)
      case None                  => ()
    }

  private def runImpl(baseDir: File, args: Seq[String]): Option[(File, Seq[String])] =
    try launch(baseDir, args) map exit
    catch {
      case r: xsbti.FullReload            => Some((baseDir, r.arguments()))
      case scala.util.control.NonFatal(e) => e.printStackTrace(); errorAndExit(e.toString)
    }

  private def launch(baseDirectory: File, arguments: Seq[String]): Option[Int] = {
    ScriptedLauncher
      .launch(
        scalaHome,
        sbtVersion,
        scalaVersion,
        bootDirectory,
        baseDirectory,
        buildinfo.TestBuildInfo.fullClasspath.toArray,
        arguments.toArray
      )
      .orElse(null) match {
      case null                   => None
      case i if i == Int.MaxValue => None
      case i                      => Some(i)
    }
  }

  private lazy val bootDirectory: File = file(sys.props("user.home")) / ".sbt" / "boot"
  private lazy val scalaHome: File = {
    val log = sbt.util.LogExchange.logger("run-from-source")
    val scalaHome0 = bootDirectory / s"scala-$scalaVersion"
    if ((scalaHome0 / "lib").exists) scalaHome0
    else {
      log.info(s"""scalaHome ($scalaHome0) wasn't found""")
      val fakeboot = file(sys.props("user.home")) / ".sbt" / "fakeboot"
      val scalaHome1 = fakeboot / s"scala-$scalaVersion"
      val scalaHome1Lib = scalaHome1 / "lib"
      val scalaHome1Temp = scalaHome1 / "temp"
      if (scalaHome1Lib.exists) log.info(s"""using $scalaHome1 that was found""")
      else {
        log.info(s"""creating $scalaHome1 by downloading scala-compiler $scalaVersion""")
        IO.createDirectories(List(scalaHome1Lib, scalaHome1Temp))
        val lm = {
          import sbt.librarymanagement.ivy.IvyDependencyResolution
          val ivyConfig = InlineIvyConfiguration().withLog(log)
          IvyDependencyResolution(ivyConfig)
        }
        val Name = """(.*)(\-[\d|\.]+)\.jar""".r
        val module = "org.scala-lang" % "scala-compiler" % scalaVersion
        lm.retrieve(module, scalaModuleInfo = None, scalaHome1Temp, log) match {
          case Right(_) =>
            (scalaHome1Temp ** "*.jar").get foreach { x =>
              val Name(head, _) = x.getName
              IO.copyFile(x, scalaHome1Lib / (head + ".jar"))
            }
          case Left(w) => sys.error(w.toString)
        }
      }
      scalaHome1
    }
  }

  private def errorAndExit(msg: String): Nothing = { System.err.println(msg); exit(1) }
  private def exit(code: Int): Nothing = System.exit(code).asInstanceOf[Nothing]
}
