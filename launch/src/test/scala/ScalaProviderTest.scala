package xsbt.boot

import java.io.File
import java.util.Properties
import xsbti._
import org.specs._
import LaunchTest._
import sbt.IO.{createDirectory, touch,withTemporaryDirectory}

object ScalaProviderTest extends Specification
{
	 def provide = addToSusVerb("provide")
	"Launch" should provide {
		"ClassLoader for Scala 2.8.0" in { checkScalaLoader("2.8.0") }
		"ClassLoader for Scala 2.8.2" in { checkScalaLoader("2.8.2") }
		"ClassLoader for Scala 2.9.0" in { checkScalaLoader("2.9.0") }
		"ClassLoader for Scala 2.9.2" in { checkScalaLoader("2.9.2") }
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
		withTemporaryDirectory { currentDirectory =>
			withLauncher { launcher =>
				Launch.run(launcher)(
					new RunConfiguration(Some(unmapScalaVersion(LaunchTest.getScalaVersion)), LaunchTest.testApp(mainClassName, extra(currentDirectory)).toID, currentDirectory, arguments)
				)
			}
		}
	private def testResources = List("test-resourceA", "a/b/test-resourceB", "sub/test-resource")
	private def createExtra(currentDirectory: File) =
	{
		val resourceDirectory = new File(currentDirectory, "resources")
		createDirectory(resourceDirectory)
		testResources.foreach(resource => touch(new File(resourceDirectory, resource.replace('/', File.separatorChar))))
		Array(resourceDirectory)
	}
	private def checkScalaLoader(version: String): Unit = withLauncher( checkLauncher(version, mapScalaVersion(version)) )
	private def checkLauncher(version: String, versionValue: String)(launcher: Launcher): Unit =
	{
		val provider = launcher.getScala(version)
		val loader = provider.loader
		// ensure that this loader can load Scala classes by trying scala.ScalaObject.
		tryScala(loader)
		getScalaVersion(loader) must beEqualTo(versionValue)
	}
	private def tryScala(loader: ClassLoader): Unit = Class.forName("scala.Product", false, loader).getClassLoader must be(loader)
}
object LaunchTest
{
	def testApp(main: String): Application = testApp(main, Array[File]())
	def testApp(main: String, extra: Array[File]): Application = Application("org.scala-sbt", "launch-test", new Explicit(AppVersion), main, Nil, false, extra)
	import Predefined._
	def testRepositories = List(Local, ScalaToolsReleases, ScalaToolsSnapshots).map(Repository.Predefined.apply)
	def withLauncher[T](f: xsbti.Launcher => T): T =
		withTemporaryDirectory { bootDirectory =>
			f(Launcher(bootDirectory, testRepositories))
		}

	def unmapScalaVersion(versionNumber: String) = versionNumber.stripSuffix(".final") 
	def mapScalaVersion(versionNumber: String) = versionNumber + ".final"
	def getScalaVersion: String = getScalaVersion(getClass.getClassLoader)
	def getScalaVersion(loader: ClassLoader): String = loadProperties(loader, "library.properties").getProperty("version.number")
	lazy val AppVersion = loadProperties(getClass.getClassLoader, "xsbt.version.properties").getProperty("version")
	private def getProperty(loader: ClassLoader, res: String, key: String) =  loadProperties(loader, res).getProperty(key)
	private def loadProperties(loader: ClassLoader, res: String): Properties =
	{
		val properties = new java.util.Properties
		val propertiesStream = loader.getResourceAsStream(res)
		try { properties.load(propertiesStream) } finally { propertiesStream.close() }
		properties
	}
}