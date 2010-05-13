/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbt.boot

import Pre._
import java.io.File
import java.net.URL

final case class LaunchConfiguration(scalaVersion: Version, ivyConfiguration: IvyOptions, app: Application, boot: BootSetup, logging: Logging, appProperties: List[AppProperty]) extends NotNull
{
	def getScalaVersion = Version.get(scalaVersion)
	def withScalaVersion(newScalaVersion: String) = LaunchConfiguration(new Version.Explicit(newScalaVersion), ivyConfiguration, app, boot, logging, appProperties)
	def withApp(app: Application) = LaunchConfiguration(scalaVersion, ivyConfiguration, app, boot, logging, appProperties)
	def withAppVersion(newAppVersion: String) = LaunchConfiguration(scalaVersion, ivyConfiguration, app.withVersion(new Version.Explicit(newAppVersion)), boot, logging, appProperties)
	def withVersions(newScalaVersion: String, newAppVersion: String) = LaunchConfiguration(new Version.Explicit(newScalaVersion), ivyConfiguration, app.withVersion(new Version.Explicit(newAppVersion)), boot, logging, appProperties)
	def map(f: File => File) = LaunchConfiguration(scalaVersion, ivyConfiguration, app.map(f), boot.map(f), logging, appProperties)
}
final case class IvyOptions(cacheDirectory: Option[File], classifiers: Classifiers, repositories: List[Repository]) extends NotNull
final case class Classifiers(forScala: List[String], app: List[String]) extends NotNull
sealed trait Version extends NotNull
object Version
{
	final class Explicit(val value: String) extends Version { override def toString = value }
	final class Implicit(val name: String, val default: Option[String]) extends Version
	{
		require(isNonEmpty(name), "Name cannot be empty")
		require(default.isEmpty || isNonEmpty(default.get), "Default cannot be the empty string")
		override def toString = name + (default match { case Some(d) => "[" + d + "]"; case None => "" })
	}

	object Implicit
	{
		def apply(s: String, name: String, default: Option[String]): Version =
			if(s == "read") new Implicit(name, default) else error("Expected 'read', got '" + s +"'")
	}
	def get(v: Version) = v  match { case e: Version.Explicit => e.value; case _ => throw new BootException("Unresolved version: " + v) }
}

final case class Application(groupID: String, name: String, version: Version, main: String, components: List[String], crossVersioned: Boolean, classpathExtra: Array[File]) extends NotNull
{
	def getVersion = Version.get(version)
	def withVersion(newVersion: Version) = Application(groupID, name, newVersion, main, components, crossVersioned, classpathExtra)
	def toID = AppID(groupID, name, getVersion, main, toArray(components), crossVersioned, classpathExtra)
	def map(f: File => File) = Application(groupID, name, version, main, components, crossVersioned, classpathExtra.map(f))
}
final case class AppID(groupID: String, name: String, version: String, mainClass: String, mainComponents: Array[String], crossVersioned: Boolean, classpathExtra: Array[File]) extends xsbti.ApplicationID

object Application
{
	def apply(id: xsbti.ApplicationID): Application =
	{
		import id._
		Application(groupID, name, new Version.Explicit(version), mainClass, mainComponents.toList, crossVersioned, classpathExtra)
	}
}

sealed trait Repository extends NotNull
object Repository
{
	final case class Maven(id: String, url: URL) extends Repository
	final case class Ivy(id: String, url: URL, pattern: String) extends Repository
	final case class Predefined(id: Predefined.Value) extends Repository

	object Predefined extends Enumeration
	{
		val Local = value("local")
		val MavenLocal = value("maven-local")
		val MavenCentral = value("maven-central")
		val ScalaToolsReleases = value("scala-tools-releases")
		val ScalaToolsSnapshots = value("scala-tools-snapshots")
		def apply(s: String): Predefined = Predefined(toValue(s))
	}

	def isMavenLocal(repo: Repository) = repo == Predefined(Predefined.MavenLocal)
	def defaults: List[Repository] = Predefined.elements.map(Predefined.apply).toList
}

final case class Search(tpe: Search.Value, paths: List[File]) extends NotNull
object Search extends Enumeration
{
	def none = Search(Current, Nil)
	val Only = value("only")
	val RootFirst = value("root-first")
	val Nearest = value("nearest")
	val Current = value("none")
	def apply(s: String, paths: List[File]): Search = Search(toValue(s), paths)
}

final case class BootSetup(directory: File, properties: File, search: Search, promptCreate: String, enableQuick: Boolean, promptFill: Boolean) extends NotNull
{
	def map(f: File => File) = BootSetup(f(directory), f(properties), search, promptCreate, enableQuick, promptFill)
}
final case class AppProperty(name: String)(val quick: Option[PropertyInit], val create: Option[PropertyInit], val fill: Option[PropertyInit]) extends NotNull

sealed trait PropertyInit extends NotNull
final class SetProperty(val value: String) extends PropertyInit
final class PromptProperty(val label: String, val default: Option[String]) extends PropertyInit

final class Logging(level: LogLevel.Value) extends NotNull
{
	import LogLevel._
	def log(s: => String, at: Value) = if(level.id <= at.id) stream(at).println("[" + at + "] " + s)
	def debug(s: => String) = log(s, Debug)
	private def stream(at: Value) = if(at == Error) System.err else System.out
}
object LogLevel extends Enumeration
{
	val Debug = value("debug", 0)
	val Info = value("info", 1)
	val Warn = value("warn", 2)
	val Error = value("error", 3)
	def apply(s: String): Logging = new Logging(toValue(s))
}

final class AppConfiguration(val arguments: Array[String], val baseDirectory: File, val provider: xsbti.AppProvider) extends xsbti.AppConfiguration