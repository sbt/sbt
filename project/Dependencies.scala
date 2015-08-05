import sbt._
import Keys._

object Dependencies {
  val bootstrapSbtVersion = "0.13.8"
  lazy val ioProj = "org.scala-sbt" % "io" % bootstrapSbtVersion
  lazy val collectionProj = "org.scala-sbt" % "collection" % bootstrapSbtVersion
  lazy val processProj = "org.scala-sbt" % "process" % bootstrapSbtVersion
  lazy val logProj = "org.scala-sbt" % "logging" % bootstrapSbtVersion
  lazy val ivyProj = "org.scala-sbt" % "ivy" % bootstrapSbtVersion

  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0-M1"

  lazy val sbinary = "org.scala-tools.sbinary" %% "sbinary" % "0.4.2"
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  lazy val specs2 = "org.specs2" %% "specs2" % "2.3.11"
  lazy val junit = "junit" % "junit" % "4.11"

  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }
}
