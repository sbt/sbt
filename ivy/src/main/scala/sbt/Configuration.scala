/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import sbt.serialization._

object Configurations {
  def config(name: String) = new Configuration(name)
  def default: Seq[Configuration] = defaultMavenConfigurations
  def defaultMavenConfigurations: Seq[Configuration] = Seq(Compile, Runtime, Test, Provided, Optional)
  def defaultInternal: Seq[Configuration] = Seq(CompileInternal, RuntimeInternal, TestInternal)
  def auxiliary: Seq[Configuration] = Seq(Sources, Docs, Pom)
  def names(cs: Seq[Configuration]) = cs.map(_.name)

  lazy val RuntimeInternal = optionalInternal(Runtime)
  lazy val TestInternal = fullInternal(Test)
  lazy val IntegrationTestInternal = fullInternal(IntegrationTest)
  lazy val CompileInternal = fullInternal(Compile)

  def internalMap(c: Configuration) = c match {
    case Compile         => CompileInternal
    case Test            => TestInternal
    case Runtime         => RuntimeInternal
    case IntegrationTest => IntegrationTestInternal
    case _               => c
  }

  def internal(base: Configuration, ext: Configuration*) = config(base.name + "-internal") extend (ext: _*) hide
  def fullInternal(base: Configuration): Configuration = internal(base, base, Optional, Provided)
  def optionalInternal(base: Configuration): Configuration = internal(base, base, Optional)

  lazy val Default = config("default")
  lazy val Compile = config("compile")
  lazy val IntegrationTest = config("it") extend (Runtime)
  lazy val Provided = config("provided")
  lazy val Docs = config("docs")
  lazy val Runtime = config("runtime") extend (Compile)
  lazy val Test = config("test") extend (Runtime)
  lazy val Sources = config("sources")
  lazy val System = config("system")
  lazy val Optional = config("optional")
  lazy val Pom = config("pom")

  lazy val ScalaTool = config("scala-tool") hide
  lazy val CompilerPlugin = config("plugin") hide

  private[sbt] val DefaultMavenConfiguration = defaultConfiguration(true)
  private[sbt] val DefaultIvyConfiguration = defaultConfiguration(false)
  private[sbt] def DefaultConfiguration(mavenStyle: Boolean) = if (mavenStyle) DefaultMavenConfiguration else DefaultIvyConfiguration
  private[sbt] def defaultConfiguration(mavenStyle: Boolean) = if (mavenStyle) Configurations.Compile else Configurations.Default
  private[sbt] def removeDuplicates(configs: Iterable[Configuration]) = Set(scala.collection.mutable.Map(configs.map(config => (config.name, config)).toSeq: _*).values.toList: _*)
}
/** Represents an Ivy configuration. */
final case class Configuration(name: String, description: String, isPublic: Boolean, extendsConfigs: List[Configuration], transitive: Boolean) {
  require(name != null && !name.isEmpty)
  require(description != null)
  def this(name: String) = this(name, "", true, Nil, true)
  def describedAs(newDescription: String) = Configuration(name, newDescription, isPublic, extendsConfigs, transitive)
  def extend(configs: Configuration*) = Configuration(name, description, isPublic, configs.toList ::: extendsConfigs, transitive)
  def notTransitive = intransitive
  def intransitive = Configuration(name, description, isPublic, extendsConfigs, false)
  def hide = Configuration(name, description, false, extendsConfigs, transitive)
  override def toString = name
}
object Configuration {
  implicit val pickler: Pickler[Configuration] with Unpickler[Configuration] = PicklerUnpickler.generate[Configuration]
}
