/* sbt -- Simple Build Tool
 * Copyright 2011 Artyom Olshevskiy
 */
package sbt

import Def.Initialize
import Keys._
import complete.{ Parser, DefaultParsers }
import classpath.ClasspathUtilities
import java.lang.reflect.{ InvocationTargetException, Method }
import java.util.Properties
import CrossVersion.partialVersion

object ScriptedPlugin extends Plugin {
  def scriptedConf = config("scripted-sbt") hide
  def scriptedLaunchConf = config("scripted-sbt-launch") hide

  val scriptedSbt = SettingKey[String]("scripted-sbt")
  val sbtLauncher = TaskKey[File]("sbt-launcher")

  val sbtTestDirectory = SettingKey[File]("sbt-test-directory")
  val scriptedBufferLog = SettingKey[Boolean]("scripted-buffer-log")

  val scriptedClasspath = TaskKey[PathFinder]("scripted-classpath")
  val scriptedTests = TaskKey[AnyRef]("scripted-tests")
  val scriptedRun = TaskKey[Method]("scripted-run")
  val scriptedLaunchOpts = SettingKey[Seq[String]]("scripted-launch-opts", "options to pass to jvm launching scripted tasks")
  val scriptedDependencies = TaskKey[Unit]("scripted-dependencies")
  val scripted = InputKey[Unit]("scripted")

  def scriptedTestsTask: Initialize[Task[AnyRef]] = (scriptedClasspath, scalaInstance) map {
    (classpath, scala) =>
      val loader = ClasspathUtilities.toLoader(classpath, scala.loader)
      ModuleUtilities.getObject("sbt.test.ScriptedTests", loader)
  }

  def scriptedRunTask: Initialize[Task[Method]] = (scriptedTests) map {
    (m) =>
      m.getClass.getMethod("run", classOf[File], classOf[Boolean], classOf[Array[String]], classOf[File], classOf[Array[String]])
  }

  import DefaultParsers._
  case class ScriptedTestPage(page: Int, total: Int)

  private[sbt] def scriptedParser(scriptedBase: File): Parser[Seq[String]] =
    {

      val scriptedFiles: NameFilter = ("test": NameFilter) | "pending"
      val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get map { (f: File) =>
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
        token("*".id | id.examples(pairMap(group)))
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
    val prereq: Unit = scriptedDependencies.value
    try {
      scriptedRun.value.invoke(
        scriptedTests.value, sbtTestDirectory.value, scriptedBufferLog.value: java.lang.Boolean,
        args.toArray, sbtLauncher.value, scriptedLaunchOpts.value.toArray)
    } catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
  }

  val scriptedSettings = Seq(
    ivyConfigurations ++= Seq(scriptedConf, scriptedLaunchConf),
    scriptedSbt := (sbtVersion in pluginCrossBuild).value,
    sbtLauncher <<= getJars(scriptedLaunchConf).map(_.get.head),
    sbtTestDirectory := sourceDirectory.value / "sbt-test",
    libraryDependencies ++= (partialVersion(scriptedSbt.value) match {
      case Some((0, 13)) =>
        Seq(
          "org.scala-sbt" % "scripted-sbt" % scriptedSbt.value % scriptedConf.toString,
          "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % scriptedLaunchConf.toString
        )
      case Some((1, _)) =>
        Seq(
          "org.scala-sbt" %% "scripted-sbt" % scriptedSbt.value % scriptedConf.toString,
          "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % scriptedLaunchConf.toString
        )
    }),
    scriptedBufferLog := true,
    scriptedClasspath := getJars(scriptedConf).value,
    scriptedTests <<= scriptedTestsTask,
    scriptedRun <<= scriptedRunTask,
    scriptedDependencies <<= (compile in Test, publishLocal) map { (analysis, pub) => Unit },
    scriptedLaunchOpts := Seq(),
    scripted <<= scriptedTask
  )
  private[this] def getJars(config: Configuration): Initialize[Task[PathFinder]] = Def.task {
    PathFinder(Classpaths.managedJars(config, classpathTypes.value, update.value).map(_.data))
  }
}
