/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */

package sbt
package test

import java.io.File
import java.nio.charset.Charset

import xsbt.IPC
import xsbt.test.{CommentHandler, FileCommands, ScriptRunner, TestScriptParser}
import IO.wrapNull

final class ScriptedTests(resourceBaseDirectory: File, bufferLog: Boolean, sbtVersion: String, defScalaVersion: String, buildScalaVersions: String, launcher: File)
{
	private val testResources = new Resources(resourceBaseDirectory)
	
	val ScriptFilename = "test"
	
	def scriptedTest(group: String, name: String, log: xsbti.Logger): Unit =
		scriptedTest(group, name, Logger.xlog2Log(log))
	def scriptedTest(group: String, name: String, log: Logger): Unit =
		testResources.readWriteResourceDirectory(group, name) { testDirectory =>
			scriptedTest(group + " / " + name, testDirectory, log)
		}
	private def scriptedTest(label: String, testDirectory: File, log: Logger): Unit =
		IPC.pullServer( scriptedTest0(label, testDirectory, log) )
	private def scriptedTest0(label: String, testDirectory: File, log: Logger)(server: IPC.Server)
	{
		FillProperties(testDirectory, sbtVersion, defScalaVersion, buildScalaVersions)
		val buffered = new BufferedLogger(new FullLogger(log))
		if(bufferLog)
			buffered.record()
		
		def createParser() =
		{
			val fileHandler = new FileCommands(testDirectory)
			val sbtHandler = new SbtHandler(testDirectory, launcher, buffered, server)
			new TestScriptParser(Map('$' -> fileHandler, '>' -> sbtHandler, '#' -> CommentHandler))
		}
		def runTest() =
		{
			val run = new ScriptRunner
			val parser = createParser()
			run(parser.parse(new File(testDirectory, ScriptFilename)))
		}

		try
		{
			runTest()
			buffered.info("+ " + label)
		}
		catch
		{
			case e: xsbt.test.TestException =>
				buffered.play()
				buffered.error("x " + label)
				if(e.getCause eq null)
					buffered.error("   " + e.getMessage)
				else
					e.printStackTrace
				throw e
			case e: Exception =>
				buffered.play()
				buffered.error("x " + label)
				throw e
		}
		finally { buffered.clear() }
	}
}
object ScriptedTests
{
	def main(args: Array[String])
	{
		val directory = new File(args(0))
		val buffer = args(1).toBoolean
		val sbtVersion = args(2)
		val defScalaVersion = args(3)
		val buildScalaVersions = args(4)
		val bootProperties = new File(args(5))
		val tests = args.drop(6)
		val logger = ConsoleLogger()
		run(directory, buffer, sbtVersion, defScalaVersion, buildScalaVersions, tests, logger, bootProperties)
	}
	def run(resourceBaseDirectory: File, bufferLog: Boolean, sbtVersion: String, defScalaVersion: String, buildScalaVersions: String, tests: Array[String], bootProperties: File): Unit =
		run(resourceBaseDirectory, bufferLog, sbtVersion, defScalaVersion, buildScalaVersions, tests, ConsoleLogger(), bootProperties)//new FullLogger(Logger.xlog2Log(log)))

	def run(resourceBaseDirectory: File, bufferLog: Boolean, sbtVersion: String, defScalaVersion: String, buildScalaVersions: String, tests: Array[String], logger: AbstractLogger, bootProperties: File)
	{
		val runner = new ScriptedTests(resourceBaseDirectory, bufferLog, sbtVersion, defScalaVersion, buildScalaVersions, bootProperties)	
		for( ScriptedTest(group, name) <- get(tests, resourceBaseDirectory, logger) )
			runner.scriptedTest(group, name, logger)
	}
	def get(tests: Seq[String], baseDirectory: File, log: Logger): Seq[ScriptedTest] =
		if(tests.isEmpty) listTests(baseDirectory, log) else parseTests(tests)
	def listTests(baseDirectory: File, log: Logger): Seq[ScriptedTest] =
		(new ListTests(baseDirectory, _ => true, log)).listTests
	def parseTests(in: Seq[String]): Seq[ScriptedTest] =
		for(testString <- in) yield
		{
			val Array(group,name) = testString.split("/").map(_.trim)
			ScriptedTest(group,name)
		}
}
object FillProperties
{
	def apply(projectDirectory: File, sbtVersion: String, defScalaVersion: String, buildScalaVersions: String): Unit =
	{
		import sbt.Path._
		fill(projectDirectory / "project" / "build.properties", sbtVersion, defScalaVersion, buildScalaVersions)
	}
	def fill(properties: File, sbtVersion: String, defScalaVersion: String, buildScalaVersions: String)
	{
		val toAppend = extraProperties(sbtVersion, defScalaVersion, buildScalaVersions)
		IO.write(properties, toAppend, Charset.forName("ISO-8859-1"), true)
	}
	def extraProperties(sbtVersion: String, defScalaVersion: String, buildScalaVersions: String) = 
<x>
sbt.version={sbtVersion}
def.scala.version={defScalaVersion}
build.scala.versions={buildScalaVersions}
</x>.text
}
final case class ScriptedTest(group: String, name: String) extends NotNull
{
	override def toString = group + "/" + name
}
private[test] object ListTests
{
	def list(directory: File, filter: java.io.FileFilter) = wrapNull(directory.listFiles(filter))
}
	import ListTests._
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