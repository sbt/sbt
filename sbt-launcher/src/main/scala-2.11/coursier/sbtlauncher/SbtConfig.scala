package coursier.sbtlauncher

import com.typesafe.config.Config
import coursier.Dependency
import coursier.util.Parse

import scala.collection.JavaConverters._

final case class SbtConfig(
  organization: String,
  moduleName: String,
  version: String,
  scalaVersion: String,
  mainClass: String,
  dependencies: Seq[Dependency]
)

object SbtConfig {

  def defaultOrganization = "org.scala-sbt"
  def defaultModuleName = "sbt"
  def defaultMainClass = "sbt.xMain"

  def fromConfig(config: Config): SbtConfig = {

    val version = config.getString("sbt.version")

    val scalaVersion =
      if (config.hasPath("scala.version"))
        config.getString("scala.version")
      else if (version.startsWith("0.13."))
        "2.10.6"
      else if (version.startsWith("1.0."))
        "2.12.1"
      else
        throw new Exception(s"Don't know what Scala version should be used for sbt version '$version'")

    val org =
      if (config.hasPath("sbt.organization"))
        config.getString("sbt.organization")
      else
        defaultOrganization

    val name =
      if (config.hasPath("sbt.module-name"))
        config.getString("sbt.module-name")
      else
        defaultModuleName

    val mainClass =
      if (config.hasPath("sbt.main-class"))
        config.getString("sbt.main-class")
      else
        defaultMainClass

    val scalaBinaryVersion = scalaVersion.split('.').take(2).mkString(".")
    val sbtBinaryVersion = version.split('.').take(2).mkString(".")

    val rawPlugins =
      if (config.hasPath("plugins"))
        config.getStringList("plugins").asScala
     else
        Nil

    val (pluginErrors, pluginsModuleVersions) = Parse.moduleVersions(rawPlugins, scalaVersion)

    if (pluginErrors.nonEmpty) {
      ???
    }

    val pluginDependencies =
      pluginsModuleVersions.map {
        case (mod, ver) =>
          Dependency(
            mod.copy(
              attributes = mod.attributes ++ Seq(
                "scalaVersion" -> scalaBinaryVersion,
                "sbtVersion" -> sbtBinaryVersion
              )
            ),
            ver
          )
      }

    val rawDeps =
      if (config.hasPath("dependencies"))
        config.getStringList("dependencies").asScala
      else
        Nil

    val (depsErrors, depsModuleVersions) = Parse.moduleVersions(rawDeps, scalaVersion)

    if (depsErrors.nonEmpty) {
      ???
    }

    val dependencies =
      depsModuleVersions.map {
        case (mod, ver) =>
          Dependency(mod, ver)
      }

    SbtConfig(
      org,
      name,
      version,
      scalaVersion,
      mainClass,
      pluginDependencies ++ dependencies
    )
  }
}
