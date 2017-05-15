
import sbt._
import sbt.Keys._

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object CrossDeps {

  import Def.setting

  // The setting / .value hoop-and-loop is necessary because of the expansion of the %%% macro, which references
  // other settings.

  def fastParse = setting("com.lihaoyi" %%% "fastparse" % "0.4.2")
  def scalazCore = setting("org.scalaz" %%% "scalaz-core" % SharedVersions.scalaz)
  def scalaJsDom = setting("org.scala-js" %%% "scalajs-dom" % "0.9.2")
  def utest = setting("com.lihaoyi" %%% "utest" % "0.4.7")
  def scalaJsJquery = setting("be.doeraene" %%% "scalajs-jquery" % "0.9.1")
  def scalaJsReact = setting("com.github.japgolly.scalajs-react" %%% "core" % "0.9.0")
}
