package xsbt.boot

import java.io.File
import java.util.Properties
import xsbti._
import org.specs._
import LaunchTest._

final class Main // needed so that when we test Launch, it doesn't think sbt was improperly downloaded (it looks for xsbt.Main to verify the right jar was downloaded)

object ScalaProviderTest extends Specification
{
	 def provide = addToSusVerb("provide")
	"Launch" should provide {
		"ClassLoader for Scala 2.7.2" in { checkScalaLoader("2.7.2") }
		"ClassLoader for Scala 2.7.3" in { checkScalaLoader("2.7.3") }
		"ClassLoader for Scala 2.7.4" in { checkScalaLoader("2.7.4") }
		"ClassLoader for Scala 2.7.5" in { checkScalaLoader("2.7.5") }
		"ClassLoader for Scala 2.7.6" in { checkScalaLoader("2.7.6") }
		"ClassLoader for Scala 2.7.7" in { checkScalaLoader("2.7.7") }
	}

	"Launch" should {
		"Successfully load an application from local repository and run it with correct arguments" in {
			checkLoad(List("test"), "xsbt.boot.test.ArgumentTest").asInstanceOf[Exit].code must be(0)
			checkLoad(List(), "xsbt.boot.test.ArgumentTest") must throwA[RuntimeException]
		}
		"Successfully load an application from local repository and run it with correct sbt version" in {
			checkLoad(List(AppVersion), "xsbt.boot.test.AppVersionTest").asInstanceOf[Exit].code must be(0)
		}
		"Add extra resources to the classpath" in {
			checkLoad(testResources, "xsbt.boot.test.ExtraTest", createExtra).asInstanceOf[Exit].code must be(0)
		}
	}

	def checkLoad(arguments: List[String], mainClassName: String): MainResult =
		checkLoad(arguments, mainClassName, _ => Array[File]())
	def checkLoad(arguments: List[String], mainClassName: String, extra: File => Array[File]): MainResult =
		FileUtilities.withTemporaryDirectory { currentDirectory =>
			withLauncher { launcher =>
				Launch.run(launcher)(
					new RunConfiguration(mapScalaVersion(LaunchTest.getScalaVersion), LaunchTest.testApp(mainClassName, extra(currentDirectory)).toID, currentDirectory, arguments)
				)
			}
		}
	private def testResources = List("test-resourceA", "a/b/test-resourceB", "sub/test-resource")
	private def createExtra(currentDirectory: File) =
	{
		val resourceDirectory = new File(currentDirectory, "resources")
		FileUtilities.createDirectory(resourceDirectory)
		testResources.foreach(resource => FileUtilities.touch(new File(resourceDirectory, resource.replace('/', File.separatorChar))))
		Array(resourceDirectory)
	}
	private def checkScalaLoader(version: String): Unit = withLauncher( checkLauncher(version, scalaVersionMap(version)) )
	private def checkLauncher(version: String, versionValue: String)(launcher: Launcher): Unit =
	{
		val provider = launcher.getScala(version)
		val loader = provider.loader
		// ensure that this loader can load Scala classes by trying scala.ScalaObject.
		tryScala(loader)
		getScalaVersion(loader) must beEqualTo(versionValue)
	}
	private def tryScala(loader: ClassLoader): Unit = Class.forName("scala.ScalaObject", false, loader).getClassLoader must be(loader)
}
object LaunchTest
{
	def testApp(main: String): Application = testApp(main, Array[File]())
	def testApp(main: String, extra: Array[File]): Application = Application("org.scala-tools.sbt", "launch-test", new Version.Explicit(AppVersion), main, Nil, false, extra)
	import Repository.Predefined._
	def testRepositories = List(Local, ScalaToolsReleases, ScalaToolsSnapshots).map(Repository.Predefined.apply)
	def withLauncher[T](f: xsbti.Launcher => T): T =
		FileUtilities.withTemporaryDirectory { bootDirectory =>
			f(new Launch(bootDirectory, testRepositories, Nil))
		}

	def mapScalaVersion(versionNumber: String) = scalaVersionMap.find(_._2 == versionNumber).getOrElse {
		error("Scala version number " + versionNumber + " from library.properties has no mapping")}._1
	val scalaVersionMap = Map( ("2.7.2", "2.7.2") ) ++ List("2.7.3", "2.7.4", "2.7.5", "2.7.6", "2.7.7").map(v => (v, v + ".final"))
	def getScalaVersion: String = getScalaVersion(getClass.getClassLoader)
	def getScalaVersion(loader: ClassLoader): String =
	{
		val propertiesStream = loader.getResourceAsStream("library.properties")
		val properties = new Properties
		properties.load(propertiesStream)
		properties.getProperty("version.number")
	}
	lazy val AppVersion =
	{
		val properties = new java.util.Properties
		println(getClass.getResource("/xsbt.version.properties"))
		val propertiesStream = getClass.getResourceAsStream("/xsbt.version.properties")
		try { properties.load(propertiesStream) } finally { propertiesStream.close() }
		 "test-" + properties.getProperty("version")
	}
}
package test
{
	class Exit(val code: Int) extends xsbti.Exit
	final class MainException(message: String) extends RuntimeException(message)
	final class ArgumentTest extends AppMain
	{
		def run(configuration: xsbti.AppConfiguration) =
			if(configuration.arguments.length == 0)
				throw new MainException("Arguments were empty")
			else
				new Exit(0)
	}
	class AppVersionTest extends AppMain
	{
		def run(configuration: xsbti.AppConfiguration) =
		{
			val expected = configuration.arguments.headOption.getOrElse("")
			if(configuration.provider.id.version == expected)
				new Exit(0)
			else
				throw new MainException("app version was " + configuration.provider.id.version + ", expected: " + expected)
		}
	}
	class ExtraTest extends AppMain
	{
		def run(configuration: xsbti.AppConfiguration) =
		{
			configuration.arguments.foreach { arg =>
				if(getClass.getClassLoader.getResource(arg) eq null)
					throw new MainException("Could not find '" + arg + "'")
			}
			new Exit(0)
		}
	}
}
