// no package
// plugins declared within no package should be visible to other plugins in the _root_ package

import sbt._, Keys._

object TopLevelImports {
  lazy val topLevelDemo = settingKey[String]("A top level demo setting.")
}

object TopA extends AutoPlugin {

  import TopLevelImports._
  import sbttest.Imports._

  val autoImport = TopLevelImports

  override def requires = sbttest.X

  override def trigger = AllRequirements

  override def projectSettings: scala.Seq[sbt.Setting[_]] = Seq(
    topLevelDemo := s"TopA: topLevelDemo project ${name.value}",
    demo := s"TopA: demo project ${name.value}"
  )

}

object TopB extends AutoPlugin {

  import TopLevelImports._

  val autoImport = TopLevelImports

  override def projectSettings: Seq[Setting[_]] = Seq(
    topLevelDemo := s"TopB: topLevelDemo project ${name.value}"
  )

}

object TopC extends AutoPlugin {

  object autoImport {
    lazy val topLevelKeyTest = settingKey[String]("A top level setting declared in a plugin.")
  }

}
