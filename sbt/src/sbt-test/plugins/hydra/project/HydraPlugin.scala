package sbtmyplugin

import sbt._
import sbt.Keys._

object HydraPlugin extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override lazy val projectSettings = Seq(
    resolvers += "Triplequote Maven Releases" at "https://repo.triplequote.com/artifactory/libs-release/",
  ) ++ inConfig(Compile)(compileSettings) ++ inConfig(Test)(compileSettings)

  private lazy val compileSettings: Seq[Def.Setting[_]] = inTask(compile)(compileInputsSettings)
  
  private def compileInputsSettings: Seq[Setting[_]] = Seq(
    scalaCompilerBridgeSource := {
      ModuleID("com.triplequote", "hydra-bridge_1_0", "2.1.4")
        .withConfigurations(Some(Configurations.Compile.name))
        .sources()
    }
  )
}
