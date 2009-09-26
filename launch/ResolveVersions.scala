package xsbt.boot

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

object ResolvedVersion extends Enumeration
{
	val Explicit, Read, Prompted = Value
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
	private def userDeclined(label: String) = throw new BootException("No " + label +" version specified.")
	private def promptVersion(label: String, default: Option[String]) =
	{
		val message = label + default.map(" [" + _ + "]").getOrElse("") + " : "
		SimpleReader.readLine(message).flatMap(x => notEmpty(x) orElse(default)) getOrElse(userDeclined(label))
	}
}

import ResolveVersions.{doNothing, notEmpty, promptVersion, readProperties, trim, userDeclined}
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
		val prompted = Seq((scalaVersion, conf.scalaVersion), (appVersion, app.version)).exists {
			case (resolved, original) => resolved.method == ResolvedVersion.Prompted &&
				( original match { case Version.Implicit(Version.Implicit.ReadOrPrompt, _) => true; case _ => false } )
		}
		val finish = if(!prompted) doNothing else () => Using( new FileOutputStream(propertiesFile) ) { out => properties.store(out, "") }
		( withVersions(scalaVersion.v, appVersion.v), finish )
	}
	private final class Resolve(versionProperty: String, label: String) extends NotNull
	{
		def noVersionInFile = throw new BootException("No " + versionProperty + " specified in " + propertiesFile)
		def apply(v: Version): ResolvedVersion =
		{
			import Version.{Explicit, Implicit}
			import Implicit.{Prompt, Read, ReadOrPrompt}
			v match
			{
				case e: Explicit => ResolvedVersion(e.value, ResolvedVersion.Explicit)
				case Implicit(Read , default) => ResolvedVersion(readVersion() orElse default getOrElse noVersionInFile, ResolvedVersion.Read )
				case Implicit(Prompt, default) => ResolvedVersion(promptVersion(label, default), ResolvedVersion.Prompted)
				case Implicit(ReadOrPrompt, default) => readOrPromptVersion(default)
			}
		}
		def readVersion() = trim(properties.getProperty(versionProperty))
		def readOrPromptVersion(default: Option[String]) =
			readVersion().map(v => ResolvedVersion(v, ResolvedVersion.Read)) getOrElse
			{
				val prompted = promptVersion(label, default)
				properties.setProperty(versionProperty, prompted)
				ResolvedVersion( prompted, ResolvedVersion.Prompted)
			}
	}
}