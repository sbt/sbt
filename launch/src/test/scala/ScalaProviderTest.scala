package xsbt

import java.util.Properties
import xsbti._
import org.specs._
import LoadHelpers._

final class Main // needed so that when we test Launch, it doesn't think sbt was improperly downloaded (it looks for xsbt.Main to verify the right jar was downloaded)

object ScalaProviderTest extends Specification
{
	 def provide = addToSusVerb("provide")
	"Launch" should provide {
		"ClassLoader for Scala 2.7.2" in { checkScalaLoader("2.7.2") }
		"ClassLoader for Scala 2.7.3" in { checkScalaLoader("2.7.3") }
		"ClassLoader for Scala 2.7.4" in { checkScalaLoader("2.7.4") }
		"ClassLoader for Scala 2.7.5" in { checkScalaLoader("2.7.5") }
	}

	"Launch" should {
		"Successfully load (stub) main sbt from local repository and run it with correct arguments" in {
			checkLoad(Array("test"), "xsbt.test.ArgumentTest").asInstanceOf[Exit].code must be(0)
			checkLoad(Array(), "xsbt.test.ArgumentTest") must throwA[RuntimeException]
		}
		"Successfully load (stub) main sbt from local repository and run it with correct sbt version" in {
			checkLoad(Array(), "xsbt.test.SbtVersionTest").asInstanceOf[Exit].code must be(0)
		}
	}

	private def checkLoad(args: Array[String], mainClassName: String): MainResult =
		withLaunch { _.load(args, test.MainTest.SbtTestVersion, mainClassName, mapScalaVersion(getScalaVersion)) }

	private def checkScalaLoader(version: String): Unit = withLaunch(checkLauncher(version, scalaVersionMap(version)))
	private def checkLauncher(version: String, versionValue: String)(launcher: ScalaProvider): Unit =
	{
		val loader = launcher.getScalaLoader(version)
		// ensure that this loader can load Scala classes by trying scala.ScalaObject.
		tryScala(loader)
		getScalaVersion(loader) must beEqualTo(versionValue)
	}
	private def tryScala(loader: ClassLoader): Unit = Class.forName("scala.ScalaObject", false, loader).getClassLoader must be(loader)
}
object LoadHelpers
{
	def withLaunch[T](f: Launcher => T): T =
		FileUtilities.withTemporaryDirectory { temp => f(new xsbt.boot.Launch(temp)) }
	def mapScalaVersion(versionNumber: String) = scalaVersionMap.find(_._2 == versionNumber).getOrElse {
		error("Scala version number " + versionNumber + " from library.properties has no mapping")}._1
	val scalaVersionMap = Map("2.7.2" -> "2.7.2") ++ Seq("2.7.3", "2.7.4", "2.7.5").map(v => (v, v + ".final"))
	def getScalaVersion: String = getScalaVersion(getClass.getClassLoader)
	def getScalaVersion(loader: ClassLoader): String =
	{
		val propertiesStream = loader.getResourceAsStream("library.properties")
		val properties = new Properties
		properties.load(propertiesStream)
		properties.getProperty("version.number")
	}
}

package test
{
	object MainTest
	{
		val SbtTestVersion = "test-0.7" // keep in sync with LauncherProject in the XSbt project definition
	}
	import MainTest.SbtTestVersion
	final class MainException(message: String) extends RuntimeException(message)
	final class ArgumentTest extends SbtMain
	{
		def run(configuration: SbtConfiguration) =
			if(configuration.arguments.length == 0)
				throw new MainException("Arguments were empty")
			else
				new xsbt.boot.Exit(0)
	}
	class SbtVersionTest extends SbtMain
	{
		def run(configuration: SbtConfiguration) =
			if(configuration.sbtVersion == SbtTestVersion)
				new xsbt.boot.Exit(0)
			else
				throw new MainException("sbt version was " + configuration.sbtVersion + ", expected: " + SbtTestVersion)
	}
}
