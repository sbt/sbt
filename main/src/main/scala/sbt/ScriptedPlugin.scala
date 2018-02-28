/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import Def.Initialize
import Keys._
import sbt.internal.util.complete.{ Parser, DefaultParsers }
import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.internal.inc.ModuleUtilities
import java.lang.reflect.Method
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._
import sbt.io._
import sbt.io.syntax._
import Project._
import Def._

object ScriptedPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  object autoImport {
    val ScriptedConf = Configurations.config("scripted-sbt") hide
    val ScriptedLaunchConf = Configurations.config("scripted-sbt-launch") hide
    val scriptedSbt = SettingKey[String]("scripted-sbt")
    val sbtLauncher = TaskKey[File]("sbt-launcher")
    val sbtTestDirectory = SettingKey[File]("sbt-test-directory")
    val scriptedBufferLog = SettingKey[Boolean]("scripted-buffer-log")
    val scriptedClasspath = TaskKey[PathFinder]("scripted-classpath")
    val scriptedTests = TaskKey[AnyRef]("scripted-tests")
    val scriptedBatchExecution =
      settingKey[Boolean]("Enables or disables batch execution for scripted.")
    val scriptedParallelInstances =
      settingKey[Int](
        "Configures the number of scripted instances for parallel testing, only used in batch mode.")
    val scriptedRun = TaskKey[Method]("scripted-run")
    val scriptedLaunchOpts = SettingKey[Seq[String]](
      "scripted-launch-opts",
      "options to pass to jvm launching scripted tasks")
    val scriptedDependencies = TaskKey[Unit]("scripted-dependencies")
    val scripted = InputKey[Unit]("scripted")
  }

  import autoImport._

  override lazy val globalSettings = Seq(
    scriptedBufferLog := true,
    scriptedLaunchOpts := Seq(),
  )

  override lazy val projectSettings = Seq(
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
    scriptedDependencies := {
      def use[A](@deprecated("unused", "") x: A*): Unit = () // avoid unused warnings
      val analysis = (Keys.compile in Test).value
      val pub = (publishLocal).value
      use(analysis, pub)
    },
    scripted := scriptedTask.evaluated
  )

  private[sbt] def scriptedTestsTask: Initialize[Task[AnyRef]] =
    Def.task {
      val loader = ClasspathUtilities.toLoader(scriptedClasspath.value, scalaInstance.value.loader)
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
        clazz.getMethod("runInParallel", fCls, bCls, asCls, fCls, asCls, lfCls, iCls)
      else
        clazz.getMethod("run", fCls, bCls, asCls, fCls, asCls, lfCls)

    Def.task(method)
  }

  import DefaultParsers._
  private[sbt] case class ScriptedTestPage(page: Int, total: Int)

  private[sbt] def scriptedParser(scriptedBase: File): Parser[Seq[String]] = {

    val scriptedFiles: NameFilter = ("test": NameFilter) | "pending"
    val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get map {
      (f: File) =>
        val p = f.getParentFile
        (p.getParentFile.getName, p.getName)
    }
    val pairMap = pairs.groupBy(_._1).mapValues(_.map(_._2).toSet);

    val id = charClass(c => !c.isWhitespace && c != '/').+.string
    val groupP = token(id.examples(pairMap.keySet.toSet)) <~ token('/')

    // A parser for page definitions
    val pageP: Parser[ScriptedTestPage] = ("*" ~ NatBasic ~ "of" ~ NatBasic) map {
      case _ ~ page ~ _ ~ total => ScriptedTestPage(page, total)
    }
    // Grabs the filenames from a given test group in the current page definition.
    def pagedFilenames(group: String, page: ScriptedTestPage): Seq[String] = {
      val files = pairMap(group).toSeq.sortBy(_.toLowerCase)
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
      } yield files map (f => group + '/' + f)

    val testID = (for (group <- groupP; name <- nameP(group)) yield (group, name))
    val testIdAsGroup = matched(testID) map (test => Seq(test))
    //(token(Space) ~> matched(testID)).*
    (token(Space) ~> (PagedIds | testIdAsGroup)).* map (_.flatten)
  }

  private[sbt] def scriptedTask: Initialize[InputTask[Unit]] = Def.inputTask {
    val args = scriptedParser(sbtTestDirectory.value).parsed
    scriptedDependencies.value
    try {
      val method = scriptedRun.value
      val scriptedInstance = scriptedTests.value
      val dir = sbtTestDirectory.value
      val log: java.lang.Boolean = scriptedBufferLog.value
      val launcher = sbtLauncher.value
      val opts = scriptedLaunchOpts.value.toArray
      val empty = new java.util.ArrayList[File]()
      val instances: java.lang.Integer = scriptedParallelInstances.value

      if (scriptedBatchExecution.value)
        method.invoke(scriptedInstance, dir, log, args.toArray, launcher, opts, empty, instances)
      else method.invoke(scriptedInstance, dir, log, args.toArray, launcher, opts, empty)
    } catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
  }

  private[this] def getJars(config: Configuration): Initialize[Task[PathFinder]] = Def.task {
    PathFinder(Classpaths.managedJars(config, classpathTypes.value, Keys.update.value).map(_.data))
  }
}
