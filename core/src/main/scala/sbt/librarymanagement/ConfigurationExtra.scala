/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import scala.annotation.{ nowarn, tailrec }
import scala.quoted.*

object Configurations {
  inline def config(name: String): Configuration = ${
    ConfigurationMacro.configMacroImpl('{ name })
  }
  def default: Vector[Configuration] = defaultMavenConfigurations
  def defaultMavenConfigurations: Vector[Configuration] =
    Vector(Compile, Runtime, Test, Provided, Optional)
  def defaultInternal: Vector[Configuration] =
    Vector(CompileInternal, RuntimeInternal, TestInternal)
  def auxiliary: Vector[Configuration] = Vector(Pom)
  def names(cs: Vector[Configuration]): Vector[String] = cs.map(_.name)
  def refs(cs: Vector[Configuration]): Vector[ConfigRef] = cs.map(_.toConfigRef)

  lazy val RuntimeInternal = optionalInternal(Runtime)
  lazy val TestInternal = fullInternal(Test)
  @nowarn
  lazy val IntegrationTestInternal = fullInternal(IntegrationTest)
  lazy val CompileInternal = fullInternal(Compile)

  @nowarn
  def internalMap(c: Configuration) = c match {
    case Compile         => CompileInternal
    case Test            => TestInternal
    case Runtime         => RuntimeInternal
    case IntegrationTest => IntegrationTestInternal
    case _               => c
  }

  private[sbt] def internal(base: Configuration, ext: Configuration*) =
    Configuration.of(base.id + "Internal", base.name + "-internal").extend(ext: _*).hide
  private[sbt] def fullInternal(base: Configuration): Configuration =
    internal(base, base, Optional, Provided)
  private[sbt] def optionalInternal(base: Configuration): Configuration =
    internal(base, base, Optional)

  lazy val Default = Configuration.of("Default", "default")
  lazy val Compile = Configuration.of("Compile", "compile")
  @deprecated("Create a separate subproject for testing instead", "1.9.0")
  lazy val IntegrationTest = Configuration.of("IntegrationTest", "it") extend (Runtime)
  lazy val Provided = Configuration.of("Provided", "provided")
  lazy val Runtime = Configuration.of("Runtime", "runtime") extend (Compile)
  lazy val Test = Configuration.of("Test", "test") extend (Runtime)
  lazy val System = Configuration.of("System", "system")
  lazy val Optional = Configuration.of("Optional", "optional")
  lazy val Pom = Configuration.of("Pom", "pom")

  lazy val ScalaTool = Configuration.of("ScalaTool", "scala-tool").hide
  lazy val ScalaDocTool = Configuration.of("ScalaDocTool", "scala-doc-tool").hide
  lazy val CompilerPlugin = Configuration.of("CompilerPlugin", "plugin").hide
  lazy val Component = Configuration.of("Component", "component").hide

  private[sbt] val DefaultMavenConfiguration = defaultConfiguration(true)
  private[sbt] val DefaultIvyConfiguration = defaultConfiguration(false)
  private[sbt] def DefaultConfiguration(mavenStyle: Boolean) =
    if (mavenStyle) DefaultMavenConfiguration else DefaultIvyConfiguration
  private[sbt] def defaultConfiguration(mavenStyle: Boolean) =
    if (mavenStyle) Configurations.Compile else Configurations.Default
  private[sbt] def removeDuplicates(configs: Iterable[Configuration]) =
    Set(
      scala.collection.mutable
        .Map(configs.map(config => (config.name, config)).toSeq: _*)
        .values
        .toList: _*
    )

  /** Returns true if the configuration should be under the influence of scalaVersion. */
  @nowarn
  private[sbt] def underScalaVersion(c: Configuration): Boolean =
    c match {
      case Default | Compile | IntegrationTest | Provided | Runtime | Test | Optional |
          CompilerPlugin | CompileInternal | RuntimeInternal | TestInternal =>
        true
      case config =>
        config.extendsConfigs exists underScalaVersion
    }
}

private[librarymanagement] abstract class ConfigurationExtra {
  def id: String
  def name: String
  def description: String
  def isPublic: Boolean
  def extendsConfigs: Vector[Configuration]
  def transitive: Boolean

  require(name != null && !name.isEmpty)
  require(description != null)

  def describedAs(newDescription: String) =
    Configuration.of(id, name, newDescription, isPublic, extendsConfigs, transitive)
  def extend(configs: Configuration*) =
    Configuration.of(
      id,
      name,
      description,
      isPublic,
      configs.toVector ++ extendsConfigs,
      transitive
    )
  def notTransitive = intransitive
  def intransitive = Configuration.of(id, name, description, isPublic, extendsConfigs, false)
  def hide = Configuration.of(id, name, description, false, extendsConfigs, transitive)
}

private[sbt] object ConfigurationMacro:
  def configMacroImpl(name: Expr[String])(using Quotes): Expr[Configuration] =
    import quotes.reflect.*
    def enclosingTerm(sym: Symbol): Symbol =
      sym match
        case sym if sym.flags is Flags.Macro => enclosingTerm(sym.owner)
        case sym if !sym.isTerm              => enclosingTerm(sym.owner)
        case _                               => sym
    val term = enclosingTerm(Symbol.spliceOwner)
    if !term.isValDef then
      report.error(
        """config must be directly assigned to a val, such as `val Tooling = config("tooling")`."""
      )

    val enclosingValName = term.name
    if enclosingValName.head.isLower then report.error("configuration id must be capitalized")
    val id = Expr(enclosingValName)
    '{ Configuration.of($id, $name) }
end ConfigurationMacro

private[librarymanagement] abstract class ConfigRefFunctions {
  implicit def configToConfigRef(c: Configuration): ConfigRef =
    c.toConfigRef
}
