package xsbt.boot

import java.io.File
import java.net.URL

final case class LaunchConfiguration(scalaVersion: Version, app: Application, repositories: Seq[Repository], boot: BootSetup, logging: Logging, appProperties: Seq[AppProperty]) extends NotNull
{
	def getScalaVersion = Version.get(scalaVersion)
	def withScalaVersion(newScalaVersion: String) = LaunchConfiguration(Version.Explicit(newScalaVersion), app, repositories, boot, logging, appProperties)
	def withApp(app: Application) = LaunchConfiguration(scalaVersion, app, repositories, boot, logging, appProperties)
	def withAppVersion(newAppVersion: String) = LaunchConfiguration(scalaVersion, app.withVersion(Version.Explicit(newAppVersion)), repositories, boot, logging, appProperties)
	def withVersions(newScalaVersion: String, newAppVersion: String) = LaunchConfiguration(Version.Explicit(newScalaVersion), app.withVersion(Version.Explicit(newAppVersion)), repositories, boot, logging, appProperties)
	def map(f: File => File) = LaunchConfiguration(scalaVersion, app, repositories, boot.map(f), logging, appProperties)
}

sealed trait Version extends NotNull
object Version
{
	final case class Explicit(value: String) extends Version
	final case class Implicit(default: Option[String]) extends Version
	{
		require(default.isEmpty || !default.get.isEmpty, "Default cannot be empty")
	}

	object Implicit
	{
		def apply(s: String, default: Option[String]): Either[String, Implicit] =
			if(s == "read") Right(Implicit(default)) else Left("Expected 'read', got '" + s +"'")
	}
	def get(v: Version) = v  match { case Version.Explicit(v) => v; case _ => throw new BootException("Unresolved version: " + v) }
	def default = Implicit(None)
}

sealed abstract class RichEnum extends Enumeration
{
	def fromString(s: String): Either[String, Value] = elements.find(_.toString == s).toRight("Expected one of " + elements.mkString(",") + " (got: " + s + ")")
	def toValue(s: String): Value = fromString(s) match { case Left(msg) => error(msg); case Right(t) => t }
}

final case class Application(groupID: String, name: String, version: Version, main: String, components: Seq[String], crossVersioned: Boolean) extends NotNull
{
	def getVersion = Version.get(version)
	def withVersion(newVersion: Version) = Application(groupID, name, newVersion, main, components, crossVersioned)
	def toID = AppID(groupID, name, getVersion, main, components.toArray, crossVersioned)
}
final case class AppID(groupID: String, name: String, version: String, mainClass: String, mainComponents: Array[String], crossVersioned: Boolean) extends xsbti.ApplicationID

object Application
{
	def apply(id: xsbti.ApplicationID): Application =
	{
		import id._
		Application(groupID, name, Version.Explicit(version), mainClass, mainComponents, crossVersioned)
	}
}

sealed trait Repository extends NotNull
object Repository
{
	final case class Maven(id: String, url: URL) extends Repository
	final case class Ivy(id: String, url: URL, pattern: String) extends Repository
	final case class Predefined(id: Predefined.Value) extends Repository

	object Predefined extends RichEnum
	{
		val Local = Value("local")
		val MavenLocal = Value("maven-local")
		val MavenCentral = Value("maven-central")
		val ScalaToolsReleases = Value("scala-tools-releases")
		val ScalaToolsSnapshots = Value("scala-tools-snapshots")
		def apply(s: String): Predefined = Predefined(toValue(s))
	}

	def defaults: Seq[Repository] = Predefined.elements.map(Predefined.apply).toList
}

final case class Search(tpe: Search.Value, paths: Seq[File]) extends NotNull
object Search extends RichEnum
{
	def none = Search(Current, Nil)
	val Only = Value("only")
	val RootFirst = Value("root-first")
	val Nearest = Value("nearest")
	val Current = Value("none")
	def apply(s: String, paths: Seq[File]): Search = Search(toValue(s), paths)
}

final case class BootSetup(directory: File, properties: File, search: Search, promptCreate: String, enableQuick: Boolean, promptFill: Boolean) extends NotNull
{
	def map(f: File => File) = BootSetup(f(directory), f(properties), search, promptCreate, enableQuick, promptFill)
}
final case class AppProperty(name: String)(val quick: Option[PropertyInit], val create: Option[PropertyInit], val fill: Option[PropertyInit]) extends NotNull

sealed trait PropertyInit extends NotNull
final case class SetProperty(value: String) extends PropertyInit
final case class PromptProperty(label: String, default: Option[String]) extends PropertyInit

final case class Logging(level: LogLevel.Value) extends NotNull
object LogLevel extends RichEnum
{
	val Debug = Value("debug")
	val Info = Value("info")
	val Warn = Value("warn")
	val Error = Value("error")
	def apply(s: String): Logging = Logging(toValue(s))
}

final class AppConfiguration(val arguments: Array[String], val baseDirectory: File, val provider: xsbti.AppProvider) extends xsbti.AppConfiguration
// The exception to use when an error occurs at the launcher level (and not a nested exception).
// This indicates overrides toString because the exception class name is not needed to understand
// the error message.
final class BootException(override val toString: String) extends RuntimeException