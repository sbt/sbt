package test

import sbt._

object GlobalAutoPlugin extends AutoPlugin {

  object autoImport {
    lazy val globalAutoPluginSetting = settingKey[String]("A top level setting declared in a plugin.")
  }

}
