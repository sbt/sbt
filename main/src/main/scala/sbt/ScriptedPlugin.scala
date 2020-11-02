/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.lang.reflect.Method

import sbt.Def._
import sbt.Keys._
import sbt.nio.Keys._
import sbt.Project._
import sbt.internal.inc.ModuleUtilities
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.util.complete.{ DefaultParsers, Parser }
import sbt.io._
import sbt.io.syntax._
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._
import sbt.nio.file.{ Glob, RecursiveGlob }

object ScriptedPlugin extends AutoPlugin {

  // Force Log4J to not use a thread context classloader otherwise it throws a CCE
  sys.props(org.apache.logging.log4j.util.LoaderUtil.IGNORE_TCCL_PROPERTY) = "true"

  object autoImport {
    val ScriptedConf = Configurations.config("scripted-sbt") hide
    val ScriptedLaunchConf = Configurations.config("scripted-sbt-launch") hide

    val scriptedSbt = settingKey[String]("")
    val sbtLauncher = taskKey[File]("")
    val sbtTestDirectory = settingKey[File]("")
    val scriptedBufferLog = settingKey[Boolean]("")
    val scriptedClasspath = taskKey[PathFinder]("")
    val scriptedTests = taskKey[AnyRef]("")
    val scriptedBatchExecution =
      settingKey[Boolean]("Enables or disables batch execution for scripted.")
    val scriptedParallelInstances = settingKey[Int](
      "Configures the number of scripted instances for parallel testing, only used in batch mode."
    )
    val scriptedRun = taskKey[Method]("")
    val scriptedLaunchOpts =
      settingKey[Seq[String]]("options to pass to jvm launching scripted tasks")
    val scriptedDependencies = taskKey[Unit]("")
    val scriptedReuseServer = settingKey[Boolean]("")
    val scripted = inputKey[Unit]("")
  }
  import autoImport._

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    scriptedBufferLog := true,
    scriptedLaunchOpts := Seq(),
  )

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    ivyConfigurations ++= Seq(ScriptedConf, ScriptedLaunchConf),
    scriptedSbt := (sbtVersion in pluginCrossBuild).value,
    sbtLauncher := getJars(ScriptedLaunchConf).map(_.get.head).value,
    sbtTestDirectory := sourceDirectory.value / "sbt-test",
    libraryDependencies ++= (CrossVersion.partialVersion(scriptedSbt.value) match {
      case Some((0, 13)) =>
        Seq(
          "org.scala-sbt" % "scripted-sbt" % scriptedSbt.value % ScriptedConf,
          "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % ScriptedLaunchConf
        )
      case Some((1, _)) =>
        Seq(
          "org.scala-sbt" %% "scripted-sbt" % scriptedSbt.value % ScriptedConf,
          "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % ScriptedLaunchConf
        )
      case Some((x, y)) => sys error s"Unknown sbt version ${scriptedSbt.value} ($x.$y)"
      case None         => sys error s"Unknown sbt version ${scriptedSbt.value}"
    }),
    scriptedClasspath := getJars(ScriptedConf).value,
    scriptedTests := scriptedTestsTask.value,
    scriptedParallelInstances := 1,
    scriptedBatchExecution := false,
    scriptedRun := scriptedRunTask.value,
    scriptedReuseServer := true,
    scriptedDependencies := {
      def use[A](@deprecated("unused", "") x: A*): Unit = () // avoid unused warnings
      val analysis = (Keys.compile in Test).value
      val pub = (publishLocal).value
      use(analysis, pub)
    },
    scripted := scriptedTask.evaluated,
    watchTriggers in scripted += Glob(sbtTestDirectory.value, RecursiveGlob)
  )

  private[sbt] def scriptedTestsTask: Initialize[Task[AnyRef]] =
    Def.task {
      val cp = scriptedClasspath.value.get.map(_.toPath)
      val loader = ClasspathUtil.toLoader(cp, scalaInstance.value.loader)
      try {
        ModuleUtilities.getObject("sbt.scriptedtest.ScriptedTests", loader)
      } catch {
        case _: ClassNotFoundException =>
          ModuleUtilities.getObject("sbt.test.ScriptedTests", loader)
      }
    }

  private[sbt] def scriptedRunTask: Initialize[Task[Method]] = Def.taskDyn {
    val fCls = classOf[File]
    val bCls = classOf[Boolean]
    val asCls = classOf[Array[String]]
    val lfCls = classOf[java.util.List[File]]
    val iCls = classOf[Int]

    val clazz = scriptedTests.value.getClass
    val method =
      if (scriptedBatchExecution.value)
        clazz.getMethod("runInParallel", fCls, bCls, asCls, fCls, asCls, lfCls, iCls, bCls)
      else
        clazz.getMethod("run", fCls, bCls, asCls, fCls, asCls, lfCls, bCls)

    Def.task(method)
  }

  private[sbt] final case class ScriptedTestPage(page: Int, total: Int)

  private[sbt] def scriptedParser(scriptedBase: File): Parser[Seq[String]] = {
    import DefaultParsers._

    val scriptedFiles
        : NameFilter = ("test": NameFilter) | "test.script" | "pending" | "pending.script"
    val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get map {
      (f: File) =>
        val p = f.getParentFile
        (p.getParentFile.getName, p.getName)
    }
    val pairMap = pairs.groupBy(_._1).mapValues(_.map(_._2).toSet)

    val id = charClass(c => !c.isWhitespace && c != '/', "not whitespace and not '/'").+.string
    val groupP = token(id.examples(pairMap.keySet)) <~ token('/')

    // A parser for page definitions
    val pageNumber = (NatBasic & not('0', "zero page number")).flatMap { i =>
      if (i <= pairs.size) Parser.success(i)
      else Parser.failure(s"$i exceeds the number of tests (${pairs.size})")
    }
    val pageP: Parser[ScriptedTestPage] = ("*" ~> pageNumber ~ ("of" ~> pageNumber)) flatMap {
      case (page, total) if page <= total => success(ScriptedTestPage(page, total))
      case (page, total)                  => failure(s"Page $page was greater than $total")
    }

    // Grabs the filenames from a given test group in the current page definition.
    def pagedFilenames(group: String, page: ScriptedTestPage): Seq[String] = {
      val files = pairMap.get(group).toSeq.flatten.sortBy(_.toLowerCase)
      val pageSize = files.size / page.total
      // The last page may loose some values, so we explicitly keep them
      val dropped = files.drop(pageSize * (page.page - 1))
      if (page.page == page.total) dropped
      else dropped.take(pageSize)
    }

    def nameP(group: String) = {
      token("*".id | id.examples(pairMap.getOrElse(group, Set.empty[String])))
    }

    val PagedIds: Parser[Seq[String]] =
      for {
        group <- groupP
        page <- pageP
        files = pagedFilenames(group, page)
        // TODO -  Fail the parser if we don't have enough files for the given page size
        //if !files.isEmpty
      } yield files map (f => s"$group/$f")

    val testID = (for (group <- groupP; name <- nameP(group)) yield (group, name))
    val testIdAsGroup = matched(testID) map (test => Seq(test))

    //(token(Space) ~> matched(testID)).*
    (token(Space) ~> (PagedIds | testIdAsGroup)).* map (_.flatten)
  }

  private[sbt] def scriptedTask: Initialize[InputTask[Unit]] = Def.inputTask {
    val args = scriptedParser(sbtTestDirectory.value).parsed
    Def.unit(scriptedDependencies.value)
    try {
      val method = scriptedRun.value
      val scriptedInstance = scriptedTests.value
      val dir = sbtTestDirectory.value
      val log = Boolean box scriptedBufferLog.value
      val launcher = sbtLauncher.value
      val opts = scriptedLaunchOpts.value.toArray
      val empty = new java.util.ArrayList[File]()
      val instances = Int box scriptedParallelInstances.value
      val reuseServer = Boolean box scriptedReuseServer.value

      if (scriptedBatchExecution.value)
        method.invoke(
          scriptedInstance,
          dir,
          log,
          args.toArray,
          launcher,
          opts,
          empty,
          instances,
          reuseServer
        )
      else
        method.invoke(scriptedInstance, dir, log, args.toArray, launcher, opts, empty, reuseServer)
      ()
    } catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
  }

  private[this] def getJars(config: Configuration): Initialize[Task[PathFinder]] = Def.task {
    PathFinder(Classpaths.managedJars(config, classpathTypes.value, Keys.update.value).map(_.data))
  }
}
