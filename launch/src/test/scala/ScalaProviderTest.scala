package xsbt

import java.util.Properties
import xsbti.boot._
import org.specs._

object ScalaProviderTest extends Specification
{
	"Launch" should {
		"Provide ClassLoader for Scala 2.7.2" in { checkScalaLoader("2.7.2", "2.7.2") }
		"Provide ClassLoader for Scala 2.7.3" in { checkScalaLoader("2.7.3", "2.7.3.final") }
		"Provide ClassLoader for Scala 2.7.4" in { checkScalaLoader("2.7.4", "2.7.4.final") }
		"Provide ClassLoader for Scala 2.7.5" in { checkScalaLoader("2.7.5", "2.7.5.final") }
	}
	private def checkScalaLoader(version: String, versionValue: String): Unit = withLaunch(checkLauncher(version, versionValue))
	private def checkLauncher(version: String, versionValue: String)(launcher: Launcher): Unit =
	{
		val loader = launcher.getScalaLoader(version)
		Class.forName("scala.ScalaObject", false, loader)
		val propertiesStream = loader.getResourceAsStream("library.properties")
		val properties = new Properties
		properties.load(propertiesStream)
		properties.getProperty("version.number") must beEqualTo(versionValue)
	}
	private def withLaunch[T](f: Launcher => T): T = withLaunch("")(f)
	private def withLaunch[T](mainClass: String)(f: Launcher => T): T =
		FileUtilities.withTemporaryDirectory { temp => f(new xsbt.boot.Launch(temp, mainClass)) }
}