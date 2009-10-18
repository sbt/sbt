package xsbt.boot

import Pre._
import java.io.{File, FileInputStream}
import java.util.Properties

object ResolveVersions
{
	def apply(conf: LaunchConfiguration): LaunchConfiguration = (new ResolveVersions(conf))()
	private def trim(s: String) = if(s eq null) None else notEmpty(s.trim)
	private def notEmpty(s: String) = if(isEmpty(s)) None else Some(s)
	private def readProperties(propertiesFile: File) =
	{
		val properties = new Properties
		if(propertiesFile.exists)
			Using( new FileInputStream(propertiesFile) )( properties.load )
		properties
	}
}

import ResolveVersions.{readProperties, trim}
final class ResolveVersions(conf: LaunchConfiguration) extends NotNull
{
	private def propertiesFile = conf.boot.properties
	private lazy val properties = readProperties(propertiesFile)
	def apply(): LaunchConfiguration =
	{
		import conf._
		val appVersionProperty = app.name.toLowerCase.replaceAll("\\s+",".") + ".version"
		val scalaVersion = (new Resolve("scala.version", "Scala"))(conf.scalaVersion)
		val appVersion = (new Resolve(appVersionProperty, app.name))(app.version)
		withVersions(scalaVersion, appVersion)
	}
	private final class Resolve(versionProperty: String, label: String) extends NotNull
	{
		def noVersionInFile = throw new BootException("No " + versionProperty + " specified in " + propertiesFile)
		def apply(v: Version): String =
		{
			v match
			{
				case e: Version.Explicit => e.value
				case i: Version.Implicit => readVersion() orElse i.default getOrElse noVersionInFile
			}
		}
		def readVersion() = trim(properties.getProperty(versionProperty))
	}
}