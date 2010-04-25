/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt.test

import Scripted._
import FileUtilities.wrapNull
import java.io.File
import java.net.URLClassLoader

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
trait Scripted extends Project with MultiTaskProject
{
	def scriptedCompatibility = CompatibilityLevel.Minimal
	def scriptedBuildVersions = CompatibilityLevel.defaultVersions(scriptedCompatibility)
	def scriptedDefScala = buildScalaVersion
	def scriptedSbt = projectVersion.value.toString
	def scriptedBufferLog = true
	
	def sbtTests: Path
	def scriptedTask(dependencies: ManagedTask*) = dynamic(scriptedTests(listTests)) dependsOn(dependencies : _*)
	def scriptedMethodTask(dependencies: ManagedTask*) = multiTask(listTests.map(_.toString).toList) { (args, includeFunction) =>
		try { scriptedTests(listTests.filter(test => includeFunction(test.toString)), dependencies : _*) }
		catch { case e: TestSetupException => task { Some(e.getMessage) } named("test-setup") }
	}
	def listTests = (new ListTests(sbtTests.asFile, include _, log)).listTests
	def scriptedTests(tests: Seq[ScriptedTest], dependencies: ManagedTask*) =
	{
		val runner = new ScriptedTests(sbtTests.asFile, scriptedBufferLog, scriptedSbt, scriptedDefScala, scriptedBuildVersions)
		
		val startTask = task { None } named("scripted-test-start") dependsOn(dependencies  : _*)
		def scriptedTest(test: ScriptedTest) =
			task { runner.scriptedTest(test.group, test.name, log) } named test.toString dependsOn(startTask)
		val testTasks = tests.map(scriptedTest)
		Empty named("scripted-test-complete") dependsOn(testTasks : _*)
	}
	private def unwrapOption[T](s: T): Option[T] = if(s == null) None else Some(s)
	
	def include(test: ScriptedTest) = true
}
import scala.collection.mutable
private[test] object Scripted
{
	val SbtTestDirectoryName = "sbt-test"
	def list(directory: File, filter: java.io.FileFilter) = wrapNull(directory.listFiles(filter))
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