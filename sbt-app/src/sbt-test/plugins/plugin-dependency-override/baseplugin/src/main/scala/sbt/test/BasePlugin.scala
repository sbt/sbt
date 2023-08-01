package sbt.test

import sbt.Keys.version
import sbt.{AutoPlugin, Compile, Setting, ThisProject, inConfig, taskKey}

class BasePlugin extends AutoPlugin {
  override def requires = sbt.plugins.JvmPlugin
  override def trigger = noTrigger

  object autoImport {
    val pluginVersion = taskKey[String]("Test key to ensure dependency is overridden")
  }
}
