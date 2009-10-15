package xsbt.boot

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

object ResolvedVersion extends Enumeration
{
	val Explicit, Read = Value
}
final case class ResolvedVersion(v: String, method: ResolvedVersion.Value) extends NotNull

object ResolveVersions
{
	def apply(conf: LaunchConfiguration): (LaunchConfiguration, () => Unit) = (new ResolveVersions(conf))()
	private def doNothing = () => ()
	private def trim(s: String) = if(s eq null) None else notEmpty(s.trim)
	private def notEmpty(s: String) = if(s.isEmpty) None else Some(s)
	private def readProperties(propertiesFile: File) =
	{
		val properties = new Properties
		if(propertiesFile.exists)
			Using( new FileInputStream(propertiesFile) )( properties.load )
		properties
	}
}

import ResolveVersions.{doNothing, notEmpty, readProperties, trim}
final class ResolveVersions(conf: LaunchConfiguration) extends NotNull
{
	private def propertiesFile = conf.boot.properties
	private lazy val properties = readProperties(propertiesFile)
	def apply(): (LaunchConfiguration, () => Unit) =
	{
		import conf._
		val appVersionProperty = app.name.toLowerCase.replaceAll("\\s+",".") + ".version"
		val scalaVersion = (new Resolve("scala.version", "Scala"))(conf.scalaVersion)
		val appVersion = (new Resolve(appVersionProperty, app.name))(app.version)
		val finish = () => Using( new FileOutputStream(propertiesFile) ) { out => properties.store(out, "") }
		( withVersions(scalaVersion.v, appVersion.v), finish )
	}
	private final class Resolve(versionProperty: String, label: String) extends NotNull
	{
		def noVersionInFile = throw new BootException("No " + versionProperty + " specified in " + propertiesFile)
		def apply(v: Version): ResolvedVersion =
		{
			import Version.{Explicit, Implicit}
			v match
			{
				case e: Explicit => ResolvedVersion(e.value, ResolvedVersion.Explicit)
				case Implicit(default) => ResolvedVersion(readVersion() orElse default getOrElse noVersionInFile, ResolvedVersion.Read )
			}
		}
		def readVersion() = trim(properties.getProperty(versionProperty))
	}
}