/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File.pathSeparator

import sbt.internal.scriptedtest.ScriptedLauncher
import sbt.util.LoggerContext

import scala.annotation.tailrec
import scala.sys.process.Process

object RunFromSourceMain {
  def fork(
      workingDirectory: File,
      scalaVersion: String,
      sbtVersion: String,
      classpath: Seq[File]
  ): Process = {
    val fo = ForkOptions()
      .withOutputStrategy(OutputStrategy.StdoutOutput)
    fork(fo, workingDirectory, scalaVersion, sbtVersion, classpath)
  }

  def fork(
      fo0: ForkOptions,
      workingDirectory: File,
      scalaVersion: String,
      sbtVersion: String,
      cp: Seq[File]
  ): Process = {
    val fo = fo0
      .withWorkingDirectory(workingDirectory)
      .withRunJVMOptions((sys.props.get("sbt.ivy.home") match {
        case Some(home) => Vector(s"-Dsbt.ivy.home=$home")
        case _          => Vector()
      }) ++ fo0.runJVMOptions)
    val runner = new ForkRun(fo)
    val options =
      Vector(workingDirectory.toString, scalaVersion, sbtVersion, cp.mkString(pathSeparator))
    val context = LoggerContext()
    val log = context.logger("RunFromSourceMain.fork", None, None)
    try runner.fork("sbt.RunFromSourceMain", cp.map(_.toPath()), options, log)
    finally context.close()
  }

  def main(args: Array[String]): Unit = args match {
    case Array() =>
      sys.error(
        s"Must specify working directory, scala version and sbt version and classpath as the first three arguments"
      )
    case Array(wd, scalaVersion, sbtVersion, classpath, args @ _*) =>
      System.setProperty("jna.nosys", "true")
      if (args.exists(_.startsWith("<"))) System.setProperty("sbt.io.virtual", "false")
      val context = LoggerContext()
      try run(file(wd), scalaVersion, sbtVersion, classpath, args, context)
      finally context.close()
  }

  // this arrangement is because Scala does not always properly optimize away
  // the tail recursion in a catch statement
  @tailrec private[sbt] def run(
      baseDir: File,
      scalaVersion: String,
      sbtVersion: String,
      classpath: String,
      args: Seq[String],
      context: LoggerContext
  ): Unit =
    runImpl(baseDir, scalaVersion, sbtVersion, classpath, args, context: LoggerContext) match {
      case Some((baseDir, args)) => run(baseDir, scalaVersion, sbtVersion, classpath, args, context)
      case None                  => ()
    }

  private def runImpl(
      baseDir: File,
      scalaVersion: String,
      sbtVersion: String,
      classpath: String,
      args: Seq[String],
      context: LoggerContext,
  ): Option[(File, Seq[String])] = {
    try
      launch(
        defaultBootDirectory,
        baseDir,
        scalaVersion,
        sbtVersion,
        classpath,
        args,
        context
      ) map exit
    catch {
      case r: xsbti.FullReload => Some((baseDir, r.arguments.toSeq))
      case scala.util.control.NonFatal(e) =>
        e.printStackTrace(); errorAndExit(e.toString)
    }
  }

  private def launch(
      bootDirectory: File,
      baseDirectory: File,
      scalaVersion: String,
      sbtVersion: String,
      classpath: String,
      arguments: Seq[String],
      context: LoggerContext,
  ): Option[Int] = {
    ScriptedLauncher
      .launch(
        scalaHome(bootDirectory, scalaVersion, context),
        sbtVersion,
        scalaVersion,
        bootDirectory,
        baseDirectory,
        classpath.split(java.io.File.pathSeparator).map(file),
        arguments.toArray
      )
      .orElse(null) match {
      case null                   => None
      case i if i == Int.MaxValue => None
      case i                      => Some(i)
    }
  }

  private lazy val defaultBootDirectory: File =
    file(sys.props("user.home")) / ".sbt" / "scripted" / "boot"
  private def scalaHome(bootDirectory: File, scalaVersion: String, context: LoggerContext): File = {
    val log = context.logger("run-from-source", None, None)
    val scalaHome0 = bootDirectory / s"scala-$scalaVersion"
    if ((scalaHome0 / "lib" / "scala-library.jar").exists) scalaHome0
    else {
      log.info(s"""scalaHome ($scalaHome0) wasn't found""")
      val fakeboot = bootDirectory / "fakeboot"
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
          IvyDependencyResolution(
            ivyConfig.withResolvers(
              ivyConfig.resolvers ++ Seq(
                "scala-ea" at "https://scala-ci.typesafe.com/artifactory/scala-integration/",
                "scala-pr" at "https://scala-ci.typesafe.com/artifactory/scala-pr-validation-snapshots/",
              )
            )
          )
        }
        val Name = """(.*)(?:\-[\d.]+)\.jar""".r
        val BinPre = """(.*)(?:\-[\d.]+)-(?:bin|pre)-.*\.jar""".r
        val module = "org.scala-lang" % "scala3-compiler_3" % scalaVersion
        lm.retrieve(module, scalaModuleInfo = None, scalaHome1Temp, log) match {
          case Left(w) => throw w.resolveException
          case Right(_) =>
            val jars = (scalaHome1Temp ** "*.jar").get()
            assert(jars.nonEmpty, s"no jars for scala $scalaVersion")
            jars.foreach { f =>
              val name = f.getName match {
                case Name(name)   => name
                case BinPre(name) => name
              }
              IO.copyFile(f, scalaHome1Lib / s"$name.jar")
            }
        }
      }
      scalaHome1
    }
  }

  private def errorAndExit(msg: String): Nothing = { System.err.println(msg); exit(1) }
  private def exit(code: Int): Nothing = System.exit(code).asInstanceOf[Nothing]
}
