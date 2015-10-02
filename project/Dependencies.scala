import sbt._
import Keys._

object Dependencies {
  lazy val scala210 = "2.10.5"
  lazy val scala211 = "2.11.7"

  val utilVersion = "0.1.0-M5"
  val ioVersion = "1.0.0-M3"
  lazy val sbtIO = "org.scala-sbt" %% "io" % ioVersion
  lazy val utilCollection = "org.scala-sbt" %% "util-collection" % utilVersion
  lazy val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  lazy val utilTesting = "org.scala-sbt" %% "util-testing" % utilVersion

  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0-M1"
  lazy val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-927bc9ded7f8fba63297cddd0d5a3d01d6ad5d8d"
  lazy val jsch = "com.jcraft" % "jsch" % "0.1.46" intransitive ()
  lazy val sbtSerialization = "org.scala-sbt" %% "serialization" % "0.1.2"
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }
}
