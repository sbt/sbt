package sbt

import sbt.*
import Keys.*

object CheckPlugin extends AutoPlugin:
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  lazy val MultiJvm = config("multi-jvm")
  lazy val test1 = taskKey[Unit]("")

  override def projectConfigurations = Seq(MultiJvm)
  override def projectSettings = Seq(
    MultiJvm / test1 := {},
    commands += Command.command("checkShortKeyDisplay") { state =>
      val configNameToIdent = Project.configNameToIdent(state)
      val show = Def.showShortKey(None, configNameToIdent)
      val displayed = show.show((MultiJvm / test1).scopedKey)
      if !displayed.contains("MultiJvm") then
        sys.error(s"Expected 'MultiJvm' in short key display but got: '$displayed'")
      state.log.info(s"Config display check passed: $displayed")
      state
    },
  )
