/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt.test

import Scripted._
import FileUtilities.{classLocation, sbtJar, scalaCompilerJar, scalaLibraryJar, wrapNull}
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
	
	override def scriptedClasspath = runClasspath +++ Path.lazyPathFinder { Path.fromFile(sbtJar) :: Nil }
}
trait SbtScripted extends ScalaScripted
{
	override def scriptedDependencies = testCompile :: `package` :: Nil
	override def scriptedClasspath = 
		Path.lazyPathFinder {
			val ivy = runClasspath.get.filter(_.asFile.getName.startsWith("ivy-")).toList
			val builtSbtJar = (outputPath / defaultJarName)
			builtSbtJar :: ivy
		}
}
final case class ScriptedTest(group: String, name: String) extends NotNull
{
	override def toString = group + "/" + name
}
trait Scripted extends Project with MultiTaskProject
{
	def sbtTests: Path
	def scriptedTask(dependencies: ManagedTask*) = dynamic(scriptedTests(listTests)) dependsOn(dependencies : _*)
	def scriptedMethodTask(dependencies: ManagedTask*) = multiTask(listTests.map(_.toString).toList) { includeFunction =>
		scriptedTests(listTests.filter(test => includeFunction(test.toString)), dependencies : _*)
	}
	def listTests = (new ListTests(sbtTests.asFile, include _, log)).listTests
	def scriptedTests(tests: Seq[ScriptedTest], dependencies: ManagedTask*) =
	{
		val localLogger = new LocalLogger(log)
		lazy val runner =
		{
			// load ScriptedTests using a ClassLoader that loads from the project classpath so that the version
			// of sbt being built is tested, not the one doing the building.
			val filtered = new FilteredLoader(ClassLoader.getSystemClassLoader, Seq("sbt.", "scala.", "ch.epfl.", "org.apache.", "org.jsch."))
			val loader = new URLClassLoader(_scriptedClasspath.toArray, filtered)
			val scriptedClass = Class.forName(ScriptedClassName, true, loader)
			val scriptedConstructor = scriptedClass.getConstructor(classOf[File], classOf[ClassLoader])
			val rawRunner = scriptedConstructor.newInstance(sbtTests.asFile, loader)
			rawRunner.asInstanceOf[{def scriptedTest(group: String, name: String, log: Reflected.Logger): String}]
		}
		
		val startTask = task { None } named("scripted-test-start") dependsOn(dependencies  : _*)
		def scriptedTest(test: ScriptedTest) =
			task { unwrapOption(runner.scriptedTest(test.group, test.name, localLogger)) } named test.toString dependsOn(startTask)
		val testTasks = tests.map(scriptedTest)
		task { None } named("scripted-test-complete") dependsOn(testTasks : _*)
	}
	private def unwrapOption[T](s: T): Option[T] = if(s == null) None else Some(s)
	/** The classpath to use for scripted tests.   This ensures that the version of sbt being built is the one used for testing.*/
	private def _scriptedClasspath =
	{
		val buildClasspath = classLocation[Scripted]
		val scalaJars = List(scalaLibraryJar, scalaCompilerJar).map(_.toURI.toURL).toList
		buildClasspath :: scalaJars ::: scriptedClasspath.get.map(_.asURL).toList
	}
	def scriptedClasspath: PathFinder = Path.emptyPathFinder
	
	def include(test: ScriptedTest) = true
}
import scala.collection.mutable
private[test] object Scripted
{
	val ScriptedClassName = "sbt.test.ScriptedTests"
	val SbtTestDirectoryName = "sbt-test"
	def list(directory: File, filter: java.io.FileFilter) = wrapNull(directory.listFiles(filter))
}
private[test] final class ListTests(baseDirectory: File, accept: ScriptedTest => Boolean, log: Logger) extends NotNull
{
	def filter = DirectoryFilter -- HiddenFileFilter
	def listTests: Seq[ScriptedTest] =
	{
		System.setProperty("sbt.scala.version", "")
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