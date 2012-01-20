/* sbt -- Simple Build Tool
 * Copyright 2011 Artyom Olshevskiy
 */
package sbt

import Project.Initialize
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
		m.getClass.getMethod("run", classOf[File], classOf[Boolean], classOf[String], classOf[String], classOf[String], classOf[Array[String]], classOf[File], classOf[Seq[String]])
	}

	def scriptedTask: Initialize[InputTask[Unit]] = InputTask(_ => complete.Parsers.spaceDelimited("<arg>")) { result =>
		(scriptedDependencies, scriptedTests, scriptedRun, sbtTestDirectory, scriptedBufferLog, scriptedSbt, scriptedScalas, sbtLauncher, scriptedLaunchOpts, result) map {
			(deps, m, r, testdir, bufferlog, version, scriptedScalas, launcher, launchOpts, args) =>
			try { r.invoke(m, testdir, bufferlog: java.lang.Boolean, version.toString, scriptedScalas.build, scriptedScalas.versions, args.toArray, launcher, launchOpts) }
			catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
		}
	}

	val scriptedSettings = Seq(
		ivyConfigurations += scriptedConf,
		scriptedSbt <<= (appConfiguration)(_.provider.id.version),
		scriptedScalas <<= (scalaVersion) { (scala) => ScriptedScalas(scala, scala) },
		libraryDependencies <<= (libraryDependencies, scriptedScalas, scriptedSbt) {(deps, scalas, version) => deps :+ "org.scala-tools.sbt" % ("scripted-sbt_" + scalas.build) % version % scriptedConf.toString },
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
