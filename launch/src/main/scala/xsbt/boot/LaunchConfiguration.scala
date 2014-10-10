/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbt.boot

import Pre._
import java.io.File
import java.net.URL
import scala.collection.immutable.List

//TODO: use copy constructor, check size change
final case class LaunchConfiguration(scalaVersion: Value[String], ivyConfiguration: IvyOptions, app: Application, boot: BootSetup, logging: Logging, appProperties: List[AppProperty], serverConfig: Option[ServerConfiguration]) {
  def isServer: Boolean = serverConfig.isDefined
  def getScalaVersion = {
    val sv = Value.get(scalaVersion)
    if (sv == "auto") None else Some(sv)
  }

  def withScalaVersion(newScalaVersion: String) = LaunchConfiguration(new Explicit(newScalaVersion), ivyConfiguration, app, boot, logging, appProperties, serverConfig)
  def withApp(app: Application) = LaunchConfiguration(scalaVersion, ivyConfiguration, app, boot, logging, appProperties, serverConfig)
  def withAppVersion(newAppVersion: String) = LaunchConfiguration(scalaVersion, ivyConfiguration, app.withVersion(new Explicit(newAppVersion)), boot, logging, appProperties, serverConfig)
  // TODO: withExplicit
  def withVersions(newScalaVersion: String, newAppVersion: String, classifiers0: Classifiers) =
    LaunchConfiguration(new Explicit(newScalaVersion), ivyConfiguration.copy(classifiers = classifiers0), app.withVersion(new Explicit(newAppVersion)), boot, logging, appProperties, serverConfig)

  def map(f: File => File) = LaunchConfiguration(scalaVersion, ivyConfiguration.map(f), app.map(f), boot.map(f), logging, appProperties, serverConfig.map(_ map f))
}
object LaunchConfiguration {
  // Saves a launch configuration into a file. This is only safe if it is loaded by the *same* launcher version.
  def save(config: LaunchConfiguration, f: File): Unit = {
    val out = new java.io.ObjectOutputStream(new java.io.FileOutputStream(f))
    try out.writeObject(config)
    finally out.close()
  }
  // Restores a launch configuration from a file. This is only safe if it is loaded by the *same* launcher version.  
  def restore(url: URL): LaunchConfiguration = {
    val in = new java.io.ObjectInputStream(url.openConnection.getInputStream)
    try in.readObject.asInstanceOf[LaunchConfiguration]
    finally in.close()
  }
}
final case class ServerConfiguration(lockFile: File, jvmArgs: Option[File], jvmPropsFile: Option[File]) {
  def map(f: File => File) =
    ServerConfiguration(f(lockFile), jvmArgs map f, jvmPropsFile map f)
}
final case class IvyOptions(ivyHome: Option[File], classifiers: Classifiers, repositories: List[Repository.Repository], checksums: List[String], isOverrideRepositories: Boolean) {
  def map(f: File => File) = IvyOptions(ivyHome.map(f), classifiers, repositories, checksums, isOverrideRepositories)
}
sealed trait Value[T] extends Serializable
final class Explicit[T](val value: T) extends Value[T] {
  override def toString = value.toString
}
final class Implicit[T](val name: String, val default: Option[T]) extends Value[T] {
  require(isNonEmpty(name), "Name cannot be empty")
  override def toString = name + (default match { case Some(d) => "[" + d + "]"; case None => "" })
}
object Value {
  def get[T](v: Value[T]): T = v match { case e: Explicit[T] => e.value; case _ => throw new BootException("Unresolved version: " + v) }
  def readImplied[T](s: String, name: String, default: Option[String])(implicit read: String => T): Value[T] =
    if (s == "read") new Implicit(name, default map read) else Pre.error("Expected 'read', got '" + s + "'")
}

final case class Classifiers(forScala: Value[List[String]], app: Value[List[String]])
object Classifiers {
  def apply(forScala: List[String], app: List[String]): Classifiers = Classifiers(new Explicit(forScala), new Explicit(app))
}

object LaunchCrossVersion {
  def apply(s: String): xsbti.CrossValue =
    s match {
      case x if CrossVersionUtil.isFull(s)     => xsbti.CrossValue.Full
      case x if CrossVersionUtil.isBinary(s)   => xsbti.CrossValue.Binary
      case x if CrossVersionUtil.isDisabled(s) => xsbti.CrossValue.Disabled
      case x                                   => Pre.error("Unknown value '" + x + "' for property 'cross-versioned'")
    }
}

final case class Application(groupID: String, name: String, version: Value[String], main: String, components: List[String], crossVersioned: xsbti.CrossValue, classpathExtra: Array[File]) {
  def getVersion = Value.get(version)
  def withVersion(newVersion: Value[String]) = Application(groupID, name, newVersion, main, components, crossVersioned, classpathExtra)
  def toID = AppID(groupID, name, getVersion, main, toArray(components), crossVersioned, classpathExtra)
  def map(f: File => File) = Application(groupID, name, version, main, components, crossVersioned, classpathExtra.map(f))
}
final case class AppID(groupID: String, name: String, version: String, mainClass: String, mainComponents: Array[String], crossVersionedValue: xsbti.CrossValue, classpathExtra: Array[File]) extends xsbti.ApplicationID {
  def crossVersioned: Boolean = crossVersionedValue != xsbti.CrossValue.Disabled
}

object Application {
  def apply(id: xsbti.ApplicationID): Application =
    {
      import id._
      Application(groupID, name, new Explicit(version), mainClass, mainComponents.toList, safeCrossVersionedValue(id), classpathExtra)
    }

  private def safeCrossVersionedValue(id: xsbti.ApplicationID): xsbti.CrossValue =
    try id.crossVersionedValue
    catch {
      case _: AbstractMethodError =>
        // Before 0.13 this method did not exist on application, so we need to provide a default value
        //in the event we're dealing with an older Application.
        if (id.crossVersioned) xsbti.CrossValue.Binary
        else xsbti.CrossValue.Disabled
    }
}

object Repository {
  trait Repository extends xsbti.Repository {
    def bootOnly: Boolean
  }
  final case class Maven(id: String, url: URL, bootOnly: Boolean = false) extends xsbti.MavenRepository with Repository
  final case class Ivy(id: String, url: URL, ivyPattern: String, artifactPattern: String, mavenCompatible: Boolean, bootOnly: Boolean = false, descriptorOptional: Boolean = false, skipConsistencyCheck: Boolean = false) extends xsbti.IvyRepository with Repository
  final case class Predefined(id: xsbti.Predefined, bootOnly: Boolean = false) extends xsbti.PredefinedRepository with Repository
  object Predefined {
    def apply(s: String): Predefined = new Predefined(xsbti.Predefined.toValue(s), false)
    def apply(s: String, bootOnly: Boolean): Predefined = new Predefined(xsbti.Predefined.toValue(s), bootOnly)
  }

  def isMavenLocal(repo: xsbti.Repository) = repo match { case p: xsbti.PredefinedRepository => p.id == xsbti.Predefined.MavenLocal; case _ => false }
  def defaults: List[xsbti.Repository] = xsbti.Predefined.values.map(x => Predefined(x, false)).toList
}

final case class Search(tpe: Search.Value, paths: List[File])
object Search extends Enumeration {
  def none = Search(Current, Nil)
  val Only = value("only")
  val RootFirst = value("root-first")
  val Nearest = value("nearest")
  val Current = value("none")
  def apply(s: String, paths: List[File]): Search = Search(toValue(s), paths)
}

final case class BootSetup(directory: File, lock: Boolean, properties: File, search: Search, promptCreate: String, enableQuick: Boolean, promptFill: Boolean) {
  def map(f: File => File) = BootSetup(f(directory), lock, f(properties), search, promptCreate, enableQuick, promptFill)
}
final case class AppProperty(name: String)(val quick: Option[PropertyInit], val create: Option[PropertyInit], val fill: Option[PropertyInit])

sealed trait PropertyInit
final class SetProperty(val value: String) extends PropertyInit
final class PromptProperty(val label: String, val default: Option[String]) extends PropertyInit

final class Logging(level: LogLevel.Value) extends Serializable {
  def log(s: => String, at: LogLevel.Value) = if (level.id <= at.id) stream(at).println("[" + at + "] " + s)
  def debug(s: => String) = log(s, LogLevel.Debug)
  private def stream(at: LogLevel.Value) = if (at == LogLevel.Error) System.err else System.out
}
object LogLevel extends Enumeration {
  val Debug = value("debug", 0)
  val Info = value("info", 1)
  val Warn = value("warn", 2)
  val Error = value("error", 3)
  def apply(s: String): Logging = new Logging(toValue(s))
}

final class AppConfiguration(val arguments: Array[String], val baseDirectory: File, val provider: xsbti.AppProvider) extends xsbti.AppConfiguration
