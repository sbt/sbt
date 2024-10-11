/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */
package sbt.librarymanagement

import scala.collection.concurrent.TrieMap

/**
 * A reference to Configuration.
 * @param name The name of the configuration that eventually get used by Maven.
 */
final class ConfigRef private (val name: String) extends Serializable {

  override def equals(o: Any): Boolean =
    this.eq(o.asInstanceOf[AnyRef])

  override val hashCode: Int = {
    37 * (37 * (17 + "sbt.librarymanagement.ConfigRef".##) + name.##)
  }

  override def toString: String = {
    name
  }

  private[this] def copy(name: String = name): ConfigRef = {
    ConfigRef(name)
  }

  def withName(name: String): ConfigRef = {
    copy(name = name)
  }
}

object ConfigRef extends sbt.librarymanagement.ConfigRefFunctions {
  // cache the reference to ConfigRefs
  private val cache = new TrieMap[String, ConfigRef]
  private lazy val Default = new ConfigRef("default")
  private lazy val Compile = new ConfigRef("compile")
  private lazy val IntegrationTest = new ConfigRef("it")
  private lazy val Provided = new ConfigRef("provided")
  private lazy val Runtime = new ConfigRef("runtime")
  private lazy val Test = new ConfigRef("test")
  private lazy val System = new ConfigRef("system")
  private lazy val Optional = new ConfigRef("optional")
  private lazy val Pom = new ConfigRef("pom")
  private lazy val ScalaTool = new ConfigRef("scala-tool")
  private lazy val ScalaDocTool = new ConfigRef("scala-doc-tool")
  private lazy val CompilerPlugin = new ConfigRef("plugin")
  private lazy val Component = new ConfigRef("component")
  private lazy val RuntimeInternal = new ConfigRef("runtime-internal")
  private lazy val TestInternal = new ConfigRef("test-internal")
  private lazy val IntegrationTestInternal = new ConfigRef("it-internal")
  private lazy val CompileInternal = new ConfigRef("compile-internal")

  def apply(name: String): ConfigRef = name match {
    case "default"          => Default
    case "compile"          => Compile
    case "it"               => IntegrationTest
    case "provided"         => Provided
    case "runtime"          => Runtime
    case "test"             => Test
    case "system"           => System
    case "optional"         => Optional
    case "pom"              => Pom
    case "scala-tool"       => ScalaTool
    case "scala-doc-tool"   => ScalaDocTool
    case "plugin"           => CompilerPlugin
    case "component"        => Component
    case "runtime-internal" => RuntimeInternal
    case "test-internal"    => TestInternal
    case "it-internal"      => IntegrationTestInternal
    case "compile-internal" => CompileInternal
    case _                  => cache.getOrElseUpdate(name, new ConfigRef(name))
  }
}
