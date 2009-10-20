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
		val scalaVersion = resolve(conf.scalaVersion)
		val appVersion = resolve(app.version)
		withVersions(scalaVersion, appVersion)
	}
	def resolve(v: Version): String =
	{
		v match
		{
			case e: Version.Explicit => e.value
			case i: Version.Implicit =>
				trim(properties.getProperty(i.name)) orElse
					i.default getOrElse
					error("No " + i.name + " specified in " + propertiesFile)
		}
	}
}