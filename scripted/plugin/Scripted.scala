/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt.test

import Scripted._
import FileUtilities.wrapNull
import java.io.File
import java.net.URLClassLoader
import xsbt.ScalaInstance

trait ScalaScripted extends BasicScalaProject with Scripted with MavenStyleScalaPaths
{
	def sbtTests = sourcePath / SbtTestDirectoryName
	def scriptedDependencies = compile :: Nil
	lazy val scripted = scriptedTask(scriptedDependencies : _*)
	lazy val testNoScripted = super.testAction
	override def testAction = testNoScripted dependsOn(scripted)
	
	lazy val scriptedOnly = scriptedMethodTask(scriptedDependencies : _*)
}
trait SbtScripted extends ScalaScripted
{
	override def scriptedDependencies = publishLocal :: Nil
}
final case class ScriptedTest(group: String, name: String) extends NotNull
{
	override def toString = group + "/" + name
}
trait Scripted extends BasicManagedProject with MultiTaskProject
{
	def scriptedCompatibility = CompatibilityLevel.Minimal
	def scriptedBuildVersions = CompatibilityLevel.defaultVersions(scriptedCompatibility)
	def scriptedDefScala = buildScalaVersion
	def scriptedSbt = projectVersion.value.toString
	def scriptedBufferLog = true

	lazy val scriptedConf = config("sbt-scripted")
	lazy val scriptedSbtDep = "org.scala-tools.sbt" % ("scripted-sbt_" + buildScalaVersion) % version.toString % scriptedConf.toString
	def scriptedClasspath = configurationClasspath(scriptedConf)

	def sbtTests: Path
	def scriptedTask(dependencies: ManagedTask*) = dynamic(scriptedTests(listTests)) dependsOn(dependencies : _*)
	def scriptedMethodTask(dependencies: ManagedTask*) = multiTask(listTests.map(_.toString).toList) { (args, includeFunction) =>
		try { scriptedTests(listTests.filter(test => includeFunction(test.toString)), dependencies : _*) }
		catch { case e: TestSetupException => task { Some(e.getMessage) } named("test-setup") }
	}
	def listTests = (new ListTests(sbtTests.asFile, include _, log)).listTests
	def scriptedTests(tests: Seq[ScriptedTest], dependencies: ManagedTask*) =
	{
		val runner = Scripted.makeScripted(buildScalaInstance, scriptedClasspath)(sbtTests.asFile, scriptedBufferLog, scriptedSbt, scriptedDefScala, scriptedBuildVersions)
		
		val startTask = task { None } named("scripted-test-start") dependsOn(dependencies  : _*)
		def scriptedTest(test: ScriptedTest) =
			task { Scripted.runScripted(runner, test.group, test.name, log) } named test.toString dependsOn(startTask)
		val testTasks = tests.map(scriptedTest)
		task {None} named("scripted-test-complete") dependsOn(testTasks : _*)
	}
	private def unwrapOption[T](s: T): Option[T] = if(s == null) None else Some(s)
	
	def include(test: ScriptedTest) = true
}
import scala.collection.mutable
private[test] object Scripted
{
	val SbtTestDirectoryName = "sbt-test"
	def list(directory: File, filter: java.io.FileFilter) = wrapNull(directory.listFiles(filter))

	def makeScripted(scalaI: ScalaInstance, cp: PathFinder)(directory: File, buffer: Boolean, sbtVersion: String, defScalaVersion: String, buildScalaVersions: String): AnyRef =
	{
		val loader = ClasspathUtilities.toLoader(cp, scalaI.loader)
		val c = Class.forName("sbt.test.ScriptedTests", true, loader)
		val con = c.getConstructor(classOf[File], classOf[Boolean], classOf[String], classOf[String], classOf[String])
		con.newInstance(directory, buffer: java.lang.Boolean, sbtVersion, defScalaVersion, buildScalaVersions).asInstanceOf[AnyRef]
	}
	def runScripted(runner: AnyRef, group: String, name: String, log: xsbti.Logger): Option[String] =
	{
		try { runner.getClass.getMethod("scriptedTest", classOf[String], classOf[String], classOf[xsbti.Logger]).invoke(runner, group, name, log); None }
		catch { case x if x.getClass.getName == "xsbt.test.TestException" => Some(x.toString) }
	}
}
private[test] final class ListTests(baseDirectory: File, accept: ScriptedTest => Boolean, log: Logger) extends NotNull
{
	def filter = DirectoryFilter -- HiddenFileFilter
	def listTests: Seq[ScriptedTest] =
	{
		list(baseDirectory, filter) flatMap { group =>
			val groupName = group.getName
			listTests(group).map(ScriptedTest(groupName, _))
		}
	}
	private[this] def listTests(group: File): Set[String] =
	{
		val groupName = group.getName
		val allTests = list(group, filter)
		if(allTests.isEmpty)
		{
			log.warn("No tests in test group " + groupName)
			Set.empty
		}
		else
		{
			val (included, skipped) = allTests.toList.partition(test => accept(ScriptedTest(groupName, test.getName)))
			if(included.isEmpty)
				log.warn("Test group " + groupName + " skipped.")
			else if(!skipped.isEmpty)
			{
				log.warn("Tests skipped in group " + group.getName + ":")
				skipped.foreach(testName => log.warn(" " + testName.getName))
			}
			Set( included.map(_.getName) : _*)
		}
	}
}

object CompatibilityLevel extends Enumeration
{
	val Full, Basic, Minimal, Minimal27, Minimal28 = Value

	def defaultVersions(level: Value) =
		level match
		{
			case Full =>  "2.7.2 2.7.3 2.7.5 2.7.7 2.8.0.Beta1 2.8.0.RC1 2.8.0.RC2 2.8.0-SNAPSHOT"
			case Basic =>  "2.7.7 2.7.2 2.8.0.RC2"
			case Minimal => "2.7.7 2.8.0.RC2"
			case Minimal27 => "2.7.7"
			case Minimal28 => "2.8.0.RC2"
		}
}