package sbt.test

import sbt.{Compile, Setting, inConfig}

object TestPlugin extends sbt.test.BasePlugin {
  override def projectSettings: Seq[Setting[_]] =
    inConfig(Compile)(settings)

  import autoImport._

  def settings: Seq[Setting[_]] = Seq(
    pluginVersion := "2.0.0"
  )
}
