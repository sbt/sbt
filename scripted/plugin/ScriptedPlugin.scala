/* sbt -- Simple Build Tool
 * Copyright 2011 Artyom Olshevskiy
 */
package sbt

import Project.Initialize
import Keys._
import classpath.ClasspathUtilities
import java.lang.reflect.{InvocationTargetException,Method}
import java.util.Properties

object ScriptedPlugin extends Plugin {
	def scriptedConf = config("scripted-sbt") hide
	private[this] def scriptedLaunchConf = config("scripted-sbt-launch") hide

	val scriptedSbt = SettingKey[String]("scripted-sbt")
	val sbtLauncher = SettingKey[File]("sbt-launcher")
	// need to get the launcher from 'update', which requires a Task, but cannot break compatibility in 0.12
	private[this] val sbtLauncherTask = TaskKey[File]("sbt-launcher-task")

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
		val parent = new sbt.classpath.FilteredLoader(scala.loader, "jline." :: Nil)
		val loader = ClasspathUtilities.toLoader(classpath, parent)
		ModuleUtilities.getObject("sbt.test.ScriptedTests", loader)
	}

	def scriptedRunTask: Initialize[Task[Method]] = (scriptedTests) map {
		(m) => try get012Method(m) catch { case e12: NoSuchMethodException =>
			try get013Method(m) catch { case _: NoSuchMethodException => throw e12 }
		}
	}
	private[this] def get012Method(m: AnyRef): Method =
		m.getClass.getMethod("run", classOf[File], classOf[Boolean], classOf[String], classOf[String], classOf[String], classOf[Array[String]], classOf[File], classOf[Array[String]])
	private[this] def get013Method(m: AnyRef): Method =
		m.getClass.getMethod("run", classOf[File], classOf[Boolean], classOf[Array[String]], classOf[File], classOf[Array[String]])


	def scriptedTask: Initialize[InputTask[Unit]] = InputTask(_ => complete.Parsers.spaceDelimited("<arg>")) { result =>
		(scriptedDependencies, scriptedTests, scriptedRun, sbtTestDirectory, scriptedBufferLog, scriptedSbt, scriptedScalas, sbtLauncherTask, scriptedLaunchOpts, result) map {
			(deps, m, r, testdir, bufferlog, version, scriptedScalas, launcher, launchOpts, args) =>
			def invoke12 = r.invoke(m, testdir, bufferlog: java.lang.Boolean, version.toString, scriptedScalas.build, scriptedScalas.versions, args.toArray, launcher, launchOpts.toArray)
			def invoke13 = r.invoke(m, testdir, bufferlog: java.lang.Boolean, args.toArray, launcher, launchOpts.toArray)
			try invoke12 catch {
				case e: InvocationTargetException => throw e.getCause
				case i: IllegalArgumentException =>
					try invoke13 catch { case e: InvocationTargetException => throw e.getCause }
			}
		}
	}

	val scriptedSettings = Seq(
		ivyConfigurations ++= Seq(scriptedConf, scriptedLaunchConf),
		scriptedSbt <<= sbtVersion,
		scriptedScalas <<= (scalaVersion) { (scala) => ScriptedScalas(scala, scala) },
		libraryDependencies <<= (libraryDependencies, scriptedSbt) {(deps, version) =>
			deps ++ Seq(
				"org.scala-sbt" % "scripted-sbt" % version % scriptedConf.toString,
				"org.scala-sbt" % "sbt-launch" % version % scriptedLaunchConf.toString from launcherURL(version)
			)
		},
		scriptedClasspath <<= getJars(scriptedConf),
		sbtLauncher <<= (appConfiguration)(app => IO.classLocationFile(app.provider.scalaProvider.launcher.getClass)),
		sbtLauncherTask <<= getJars(scriptedLaunchConf).map(_.get.head),
		sbtTestDirectory <<= sourceDirectory / "sbt-test",
		scriptedBufferLog := true,
		scriptedClasspath <<= (classpathTypes, update) map { (ct, report) => PathFinder(Classpaths.managedJars(scriptedConf, ct, report).map(_.data)) },
		scriptedTests <<= scriptedTestsTask,
		scriptedRun <<= scriptedRunTask,
		scriptedDependencies <<= (compile in Test, publishLocal) map { (analysis, pub) => Unit },
		scriptedLaunchOpts := Seq(),
		scripted <<= scriptedTask
	)
	private[this] def getJars(config: Configuration): Initialize[Task[PathFinder]] =
		(classpathTypes, update) map { (ct, report) => PathFinder(Classpaths.managedJars(config, ct, report).map(_.data)) }
	private[this] def launcherURL(v: String): String =
		"http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/" + v + "/sbt-launch.jar"
}
