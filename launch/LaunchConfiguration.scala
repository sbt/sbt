/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbt.boot

import Pre._
import java.io.File
import java.net.URL
import scala.collection.immutable.List

//TODO: use copy constructor, check size change
final case class LaunchConfiguration(scalaVersion: Value[String], ivyConfiguration: IvyOptions, app: Application, boot: BootSetup, logging: Logging, appProperties: List[AppProperty])
{
	def getScalaVersion = {
		val sv = Value.get(scalaVersion)
		if(sv == "auto") None else Some(sv)
	}

	def withScalaVersion(newScalaVersion: String) = LaunchConfiguration(new Explicit(newScalaVersion), ivyConfiguration, app, boot, logging, appProperties)
	def withApp(app: Application) = LaunchConfiguration(scalaVersion, ivyConfiguration, app, boot, logging, appProperties)
	def withAppVersion(newAppVersion: String) = LaunchConfiguration(scalaVersion, ivyConfiguration, app.withVersion(new Explicit(newAppVersion)), boot, logging, appProperties)
	// TODO: withExplicit
	def withVersions(newScalaVersion: String, newAppVersion: String, classifiers0: Classifiers) =
		LaunchConfiguration(new Explicit(newScalaVersion), ivyConfiguration.copy(classifiers = classifiers0), app.withVersion(new Explicit(newAppVersion)), boot, logging, appProperties)

	def map(f: File => File) = LaunchConfiguration(scalaVersion, ivyConfiguration.map(f), app.map(f), boot.map(f), logging, appProperties)
}
final case class IvyOptions(ivyHome: Option[File], classifiers: Classifiers, repositories: List[xsbti.Repository], checksums: List[String], isOverrideRepositories: Boolean)
{
	def map(f: File => File) = IvyOptions(ivyHome.map(f), classifiers, repositories, checksums, isOverrideRepositories)
}
sealed trait Value[T]
final class Explicit[T](val value: T) extends Value[T] {
	override def toString = value.toString
}
final class Implicit[T](val name: String, val default: Option[T]) extends Value[T]
{
	require(isNonEmpty(name), "Name cannot be empty")
	override def toString = name + (default match { case Some(d) => "[" + d + "]"; case None => "" })
}
object Value
{
	def get[T](v: Value[T]): T = v match { case e: Explicit[T] => e.value; case _ => throw new BootException("Unresolved version: " + v) }
	def readImplied[T](s: String, name: String, default: Option[String])(implicit read: String => T): Value[T] =
		if(s == "read") new Implicit(name, default map read) else error("Expected 'read', got '" + s +"'")
}

final case class Classifiers(forScala: Value[List[String]], app: Value[List[String]])
object Classifiers {
	def apply(forScala: List[String], app: List[String]):Classifiers = Classifiers(new Explicit(forScala), new Explicit(app))
}

final case class Application(groupID: String, name: String, version: Value[String], main: String, components: List[String], crossVersioned: Boolean, classpathExtra: Array[File])
{
	def getVersion = Value.get(version)
	def withVersion(newVersion: Value[String]) = Application(groupID, name, newVersion, main, components, crossVersioned, classpathExtra)
	def toID = AppID(groupID, name, getVersion, main, toArray(components), crossVersioned, classpathExtra)
	def map(f: File => File) = Application(groupID, name, version, main, components, crossVersioned, classpathExtra.map(f))
}
final case class AppID(groupID: String, name: String, version: String, mainClass: String, mainComponents: Array[String], crossVersioned: Boolean, classpathExtra: Array[File]) extends xsbti.ApplicationID

object Application
{
	def apply(id: xsbti.ApplicationID): Application =
	{
		import id._
		Application(groupID, name, new Explicit(version), mainClass, mainComponents.toList, crossVersioned, classpathExtra)
	}
}

object Repository
{
	final case class Maven(id: String, url: URL) extends xsbti.MavenRepository
	final case class Ivy(id: String, url: URL, ivyPattern: String, artifactPattern: String, mavenCompatible: Boolean) extends xsbti.IvyRepository
	final case class Predefined(id: xsbti.Predefined) extends xsbti.PredefinedRepository
	object Predefined {
		def apply(s: String): Predefined = Predefined(xsbti.Predefined.toValue(s))
	}

	def isMavenLocal(repo: xsbti.Repository) = repo match { case p: xsbti.PredefinedRepository => p.id == xsbti.Predefined.MavenLocal; case _ => false }
	def defaults: List[xsbti.Repository] = xsbti.Predefined.values.map(Predefined.apply).toList
}

final case class Search(tpe: Search.Value, paths: List[File])
object Search extends Enumeration
{
	def none = Search(Current, Nil)
	val Only = value("only")
	val RootFirst = value("root-first")
	val Nearest = value("nearest")
	val Current = value("none")
	def apply(s: String, paths: List[File]): Search = Search(toValue(s), paths)
}

final case class BootSetup(directory: File, lock: Boolean, properties: File, search: Search, promptCreate: String, enableQuick: Boolean, promptFill: Boolean)
{
	def map(f: File => File) = BootSetup(f(directory), lock, f(properties), search, promptCreate, enableQuick, promptFill)
}
final case class AppProperty(name: String)(val quick: Option[PropertyInit], val create: Option[PropertyInit], val fill: Option[PropertyInit])

sealed trait PropertyInit
final class SetProperty(val value: String) extends PropertyInit
final class PromptProperty(val label: String, val default: Option[String]) extends PropertyInit

final class Logging(level: LogLevel.Value)
{
	def log(s: => String, at: LogLevel.Value) = if(level.id <= at.id) stream(at).println("[" + at + "] " + s)
	def debug(s: => String) = log(s, LogLevel.Debug)
	private def stream(at: LogLevel.Value) = if(at == LogLevel.Error) System.err else System.out
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
