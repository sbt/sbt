/* sbt -- Simple Build Tool
 * Copyright 2011 Artyom Olshevskiy
 */
package sbt

import Def.Initialize
import Keys._
import sbt.internal.util.complete.{ Parser, DefaultParsers }
import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.internal.inc.ModuleUtilities
import java.lang.reflect.Method

import sbt.syntax._

object ScriptedPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements
  object autoImport {
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
  }
  import autoImport._
  override lazy val projectSettings = Seq(
    ivyConfigurations ++= Seq(scriptedConf, scriptedLaunchConf),
    scriptedSbt := sbtVersion.value,
    sbtLauncher := getJars(scriptedLaunchConf).map(_.get.head).value,
    sbtTestDirectory := sourceDirectory.value / "sbt-test",
    libraryDependencies ++= Seq(
      "org.scala-sbt" %% "scripted-sbt" % scriptedSbt.value % scriptedConf.toString,
      "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % scriptedLaunchConf.toString
    ),
    scriptedBufferLog := true,
    scriptedClasspath := getJars(scriptedConf).value,
    scriptedTests := scriptedTestsTask.value,
    scriptedRun := scriptedRunTask.value,
    scriptedDependencies := {
      val analysis = (compile in Test).value
      val pub = (publishLocal).value
      Unit
    },
    scriptedLaunchOpts := Seq(),
    scripted := scriptedTask.evaluated
  )

  def scriptedTestsTask: Initialize[Task[AnyRef]] = (scriptedClasspath, scalaInstance) map {
    (classpath, scala) =>
      val loader = ClasspathUtilities.toLoader(classpath, scala.loader)
      ModuleUtilities.getObject("sbt.test.ScriptedTests", loader)
  }

  def scriptedRunTask: Initialize[Task[Method]] = (scriptedTests) map {
    (m) =>
      m.getClass.getMethod("run", classOf[File], classOf[Boolean], classOf[Array[String]], classOf[File], classOf[Array[String]])
  }

  private def scriptedParser(scriptedBase: File): Parser[Seq[String]] =
    {
      import DefaultParsers._
      val pairs = (scriptedBase * AllPassFilter * AllPassFilter * "test").get map { (f: File) =>
        val p = f.getParentFile
        (p.getParentFile.getName, p.getName)
      }
      val pairMap = pairs.groupBy(_._1).mapValues(_.map(_._2).toSet)

      val id = charClass(c => !c.isWhitespace && c != '/').+.string
      val groupP = token(id.examples(pairMap.keySet)) <~ token('/')
      def nameP(group: String) = token("*".id | id.examples(pairMap(group)))
      val testID = for (group <- groupP; name <- nameP(group)) yield (group, name)
      (token(Space) ~> matched(testID)).*
    }

  def scriptedTask: Initialize[InputTask[Unit]] = Def.inputTask {
    val args = scriptedParser(sbtTestDirectory.value).parsed
    scriptedDependencies.value
    try {
      scriptedRun.value.invoke(
        scriptedTests.value, sbtTestDirectory.value, scriptedBufferLog.value: java.lang.Boolean,
        args.toArray, sbtLauncher.value, scriptedLaunchOpts.value.toArray)
    } catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
  }

  private[this] def getJars(config: Configuration): Initialize[Task[PathFinder]] = Def.task {
    PathFinder(Classpaths.managedJars(config, classpathTypes.value, update.value).map(_.data))
  }
}
