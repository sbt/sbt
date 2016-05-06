// no package declaration

import sbt._, syntax._, Keys._

object D extends AutoPlugin {
  object autoImport {
    lazy val dKey = settingKey[String]("Test key")
  }
}
