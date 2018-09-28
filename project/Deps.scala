
import sbt._
import sbt.Defaults.sbtPluginExtra
import sbt.Keys._

object Deps {

  def sbtPgp = Def.setting {
    val sbtv = CrossVersion.binarySbtVersion(sbtVersion.in(pluginCrossBuild).value)
    val sv = scalaBinaryVersion.value
    val ver = "1.1.1"
    sbtPluginExtra("com.jsuereth" % "sbt-pgp" % ver, sbtv, sv)
  }

  def jarjar = "io.get-coursier.jarjar" % "jarjar-core" % "1.0.1-coursier-1"

  def jarjarTransitiveDeps = Seq(
    "com.google.code.findbugs" % "jsr305" % "2.0.2",
    "org.ow2.asm" % "asm-commons" % "5.2",
    "org.ow2.asm" % "asm-util" % "5.2",
    "org.slf4j" % "slf4j-api" % "1.7.25"
  )

  def utest = "com.lihaoyi" %% "utest" % "0.6.4"
}
