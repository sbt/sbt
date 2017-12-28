
import sbt._
import sbt.Defaults.sbtPluginExtra
import sbt.Keys._

object Deps {

  def quasiQuotes = "org.scalamacros" %% "quasiquotes" % "2.1.0"
  def fastParse = "com.lihaoyi" %% "fastparse" % SharedVersions.fastParse
  def jsoup = "org.jsoup" % "jsoup" % "1.10.3"
  def scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
  def scalazConcurrent = "org.scalaz" %% "scalaz-concurrent" % SharedVersions.scalaz
  def caseApp = "com.github.alexarchambault" %% "case-app" % "1.1.3"
  def okhttpUrlConnection = "com.squareup.okhttp" % "okhttp-urlconnection" % "2.7.5"
  def argonautShapeless = "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M6"
  def jackson = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4"
  def scalatest = "org.scalatest" %% "scalatest" % "3.0.0"
  def junit = "junit" % "junit" % "4.12"

  def sbtPgp = Def.setting {
    val sbtv = CrossVersion.binarySbtVersion(sbtVersion.in(pluginCrossBuild).value)
    val sv = scalaBinaryVersion.value
    val ver = "1.1.0"
    sbtPluginExtra("com.jsuereth" % "sbt-pgp" % ver, sbtv, sv)
  }

  def scalaAsync = Def.setting {

    val version =
      if (scalaBinaryVersion.value == "2.10") "0.9.5"
      else "0.9.7"

    "org.scala-lang.modules" %% "scala-async" % version
  }
  
  def jarjar = "io.get-coursier.jarjar" % "jarjar-core" % "1.0.1-coursier-1"

  def jarjarTransitiveDeps = Seq(
    "com.google.code.findbugs" % "jsr305" % "2.0.2",
    "org.ow2.asm" % "asm-commons" % SharedVersions.asm,
    "org.ow2.asm" % "asm-util" % SharedVersions.asm,
    "org.slf4j" % "slf4j-api" % "1.7.25"
  )

  def scalaNativeNir = "io.get-coursier.scala-native" %% "nir" % SharedVersions.scalaNative
  def scalaNativeTools = "io.get-coursier.scala-native" %% "tools" % SharedVersions.scalaNative
  def scalaNativeUtil = "io.get-coursier.scala-native" %% "util" % SharedVersions.scalaNative
}
