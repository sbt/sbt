/* sbt -- Simple Build Tool
 * Copyright 2011 Artyom Olshevskiy
 */
package sbt

import Def.Initialize
import Keys._
import classpath.ClasspathUtilities
import java.lang.reflect.Method
import java.util.Properties

object ScriptedPlugin extends Plugin {
	def scriptedConf = config("scripted-sbt") hide

	val scriptedSbt = SettingKey[String]("scripted-sbt")
	val sbtLauncher = SettingKey[File]("sbt-launcher")
	val sbtTestDirectory = SettingKey[File]("sbt-test-directory")
	val scriptedBufferLog = SettingKey[Boolean]("scripted-buffer-log")
	final case class ScriptedScalas(build: String, versions: String)
	val scriptedScalas = SettingKey[ScriptedScalas]("scripted-scalas")

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
		m.getClass.getMethod("run", classOf[File], classOf[Boolean], classOf[String], classOf[String], classOf[String], classOf[Array[String]], classOf[File], classOf[Array[String]])
	}

	def scriptedTask: Initialize[InputTask[Unit]] = Def.inputTask {
		val args = Def.spaceDelimited().parsed
		val prereq: Unit = scriptedDependencies.value
		try {
			scriptedRun.value.invoke(
				scriptedTests.value, sbtTestDirectory.value, scriptedBufferLog.value: java.lang.Boolean,
				scriptedSbt.value.toString, scriptedScalas.value.build, scriptedScalas.value.versions,
				args.toArray, sbtLauncher.value, scriptedLaunchOpts.value.toArray)
		}
		catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
	}

	val scriptedSettings = Seq(
		ivyConfigurations += scriptedConf,
		scriptedSbt <<= (appConfiguration)(_.provider.id.version),
		scriptedScalas <<= (scalaVersion) { (scala) => ScriptedScalas(scala, scala) },
		libraryDependencies <<= (libraryDependencies, scriptedSbt) {(deps, version) => deps :+ "org.scala-sbt" % "scripted-sbt" % version % scriptedConf.toString },
		sbtLauncher <<= (appConfiguration)(app => IO.classLocationFile(app.provider.scalaProvider.launcher.getClass)),
		sbtTestDirectory <<= sourceDirectory / "sbt-test",
		scriptedBufferLog := true,
		scriptedClasspath <<= (classpathTypes, update) map { (ct, report) => PathFinder(Classpaths.managedJars(scriptedConf, ct, report).map(_.data)) },
		scriptedTests <<= scriptedTestsTask,
		scriptedRun <<= scriptedRunTask,
		scriptedDependencies <<= (compile in Test, publishLocal) map { (analysis, pub) => Unit },
		scriptedLaunchOpts := Seq(),
		scripted <<= scriptedTask
	)
}
