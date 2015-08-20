import sbt._
import Keys._

object Dependencies {
  val utilVersion = "1.0.0-SNAPSHOT"
  val bootstrapSbtVersion = "0.13.8"
  lazy val ioProj = "org.scala-sbt" % "io" % bootstrapSbtVersion
  lazy val utilCollection = "org.scala-sbt.util" %% "util-collection" % utilVersion
  lazy val utilLogging = "org.scala-sbt.util" %% "util-logging" % utilVersion

  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0-M1"
  lazy val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-927bc9ded7f8fba63297cddd0d5a3d01d6ad5d8d"
  lazy val jsch = "com.jcraft" % "jsch" % "0.1.46" intransitive ()
  lazy val sbtSerialization = "org.scala-sbt" %% "serialization" % "0.1.2"
  // lazy val sbinary = "org.scala-tools.sbinary" %% "sbinary" % "0.4.2"
  // lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  // lazy val specs2 = "org.specs2" %% "specs2" % "2.3.11"
  // lazy val junit = "junit" % "junit" % "4.11"
  // lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }
}
