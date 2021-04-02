package test

import sbt._, Keys._

object Global {
  val x = 3
}

object GlobalAutoPlugin extends AutoPlugin {

  object autoImport {
    lazy val globalAutoPluginSetting = settingKey[String]("A top level setting declared in a plugin.")
  }

}
