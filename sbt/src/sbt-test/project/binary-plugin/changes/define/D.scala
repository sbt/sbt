// no package declaration

import sbt._

object D extends AutoPlugin {

  object autoImport {
    lazy val dKey = settingKey[String]("Test key")
  }

}