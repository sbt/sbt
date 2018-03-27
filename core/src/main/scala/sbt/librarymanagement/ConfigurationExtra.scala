/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import scala.annotation.tailrec
import scala.language.experimental.macros

object Configurations {
  def config(name: String): Configuration = macro ConfigurationMacro.configMacroImpl
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
  lazy val IntegrationTestInternal = fullInternal(IntegrationTest)
  lazy val CompileInternal = fullInternal(Compile)

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
  lazy val IntegrationTest = Configuration.of("IntegrationTest", "it") extend (Runtime)
  lazy val Provided = Configuration.of("Provided", "provided")
  lazy val Runtime = Configuration.of("Runtime", "runtime") extend (Compile)
  lazy val Test = Configuration.of("Test", "test") extend (Runtime)
  lazy val System = Configuration.of("System", "system")
  lazy val Optional = Configuration.of("Optional", "optional")
  lazy val Pom = Configuration.of("Pom", "pom")

  lazy val ScalaTool = Configuration.of("ScalaTool", "scala-tool").hide
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
    Configuration.of(id,
                     name,
                     description,
                     isPublic,
                     configs.toVector ++ extendsConfigs,
                     transitive)
  def notTransitive = intransitive
  def intransitive = Configuration.of(id, name, description, isPublic, extendsConfigs, false)
  def hide = Configuration.of(id, name, description, false, extendsConfigs, transitive)
}

private[sbt] object ConfigurationMacro {
  import scala.reflect.macros._

  def configMacroImpl(c: blackbox.Context)(name: c.Expr[String]): c.Expr[Configuration] = {
    import c.universe._
    val enclosingValName = definingValName(
      c,
      methodName =>
        s"""$methodName must be directly assigned to a val, such as `val Tooling = $methodName("tooling")`.""")
    val id = c.Expr[String](Literal(Constant(enclosingValName)))
    reify { Configuration.of(id.splice, name.splice) }
  }

  def definingValName(c: blackbox.Context, invalidEnclosingTree: String => String): String = {
    import c.universe.{ Apply => ApplyTree, _ }
    val methodName = c.macroApplication.symbol.name
    def processName(n: Name): String =
      n.decodedName.toString.trim // trim is not strictly correct, but macros don't expose the API necessary
    @tailrec def enclosingVal(trees: List[c.Tree]): String = {
      trees match {
        case ValDef(_, name, _, _) :: _                      => processName(name)
        case (_: ApplyTree | _: Select | _: TypeApply) :: xs => enclosingVal(xs)
        // lazy val x: X = <methodName> has this form for some reason (only when the explicit type is present, though)
        case Block(_, _) :: DefDef(mods, name, _, _, _, _) :: _ if mods.hasFlag(Flag.LAZY) =>
          processName(name)
        case _ =>
          c.error(c.enclosingPosition, invalidEnclosingTree(methodName.decodedName.toString))
          "<error>"
      }
    }
    enclosingVal(enclosingTrees(c).toList)
  }

  def enclosingTrees(c: blackbox.Context): Seq[c.Tree] =
    c.asInstanceOf[reflect.macros.runtime.Context]
      .callsiteTyper
      .context
      .enclosingContextChain
      .map(_.tree.asInstanceOf[c.Tree])
}

private[librarymanagement] abstract class ConfigRefFunctions {
  implicit def configToConfigRef(c: Configuration): ConfigRef =
    c.toConfigRef
}
