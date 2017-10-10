/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import Def.Initialize
import Keys._
import sbt.internal.util.complete.{ Parser, DefaultParsers }
import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.internal.inc.ModuleUtilities
import java.lang.reflect.Method
import sbt.librarymanagement.CrossVersion.partialVersion

object ScriptedPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements
  object autoImport {
    val ScriptedConf = Configurations.config("scripted-sbt") hide
    val ScriptedLaunchConf = Configurations.config("scripted-sbt-launch") hide
    val scriptedSbt = SettingKey[String]("scripted-sbt")
    val sbtLauncher = TaskKey[File]("sbt-launcher")
    val sbtTestDirectory = SettingKey[File]("sbt-test-directory")
    val scriptedBufferLog = SettingKey[Boolean]("scripted-buffer-log")
    val scriptedClasspath = TaskKey[PathFinder]("scripted-classpath")
    val scriptedTests = TaskKey[AnyRef]("scripted-tests")
    val scriptedRun = TaskKey[Method]("scripted-run")
    val scriptedLaunchOpts = SettingKey[Seq[String]](
      "scripted-launch-opts",
      "options to pass to jvm launching scripted tasks")
    val scriptedDependencies = TaskKey[Unit]("scripted-dependencies")
    val scripted = InputKey[Unit]("scripted")
  }
  import autoImport._
  override lazy val projectSettings = Seq(
    ivyConfigurations ++= Seq(ScriptedConf, ScriptedLaunchConf),
    scriptedSbt := (sbtVersion in pluginCrossBuild).value,
    sbtLauncher := getJars(ScriptedLaunchConf).map(_.get.head).value,
    sbtTestDirectory := sourceDirectory.value / "sbt-test",
    libraryDependencies ++= (partialVersion(scriptedSbt.value) match {
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
    }),
    scriptedBufferLog := true,
    scriptedClasspath := getJars(ScriptedConf).value,
    scriptedTests := scriptedTestsTask.value,
    scriptedRun := scriptedRunTask.value,
    scriptedDependencies := {
      def use[A](x: A*): Unit = () // avoid unused warnings
      val analysis = (compile in Test).value
      val pub = (publishLocal).value
      use(analysis, pub)
    },
    scriptedLaunchOpts := Seq(),
    scripted := scriptedTask.evaluated
  )

  def scriptedTestsTask: Initialize[Task[AnyRef]] =
    Def.task {
      val loader = ClasspathUtilities.toLoader(scriptedClasspath.value, scalaInstance.value.loader)
      ModuleUtilities.getObject("sbt.test.ScriptedTests", loader)
    }

  def scriptedRunTask: Initialize[Task[Method]] = Def.task(
    scriptedTests.value.getClass.getMethod("run",
                                           classOf[File],
                                           classOf[Boolean],
                                           classOf[Array[String]],
                                           classOf[File],
                                           classOf[Array[String]],
                                           classOf[java.util.List[File]])
  )

  import DefaultParsers._
  case class ScriptedTestPage(page: Int, total: Int)

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

  def scriptedTask: Initialize[InputTask[Unit]] = Def.inputTask {
    val args = scriptedParser(sbtTestDirectory.value).parsed
    scriptedDependencies.value
    try {
      scriptedRun.value.invoke(
        scriptedTests.value,
        sbtTestDirectory.value,
        scriptedBufferLog.value: java.lang.Boolean,
        args.toArray,
        sbtLauncher.value,
        scriptedLaunchOpts.value.toArray,
        new java.util.ArrayList()
      )
    } catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
  }

  private[this] def getJars(config: Configuration): Initialize[Task[PathFinder]] = Def.task {
    PathFinder(Classpaths.managedJars(config, classpathTypes.value, update.value).map(_.data))
  }
}
