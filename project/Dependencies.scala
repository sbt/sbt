import sbt._
import Keys._

object Dependencies {

  val scala210 = "2.10.5"
  val scala211 = "2.11.7"

  val bootstrapSbtVersion = "0.13.8"
  val utilVersion = "1.0.0-SNAPSHOT"

  lazy val sbtIO = "org.scala-sbt" %% "io" % "1.0.0-M1"
  lazy val utilLogging = "org.scala-sbt.util" %% "util-logging" % utilVersion
  lazy val utilControl = "org.scala-sbt.util" %% "util-control" % utilVersion
  lazy val processProj = "org.scala-sbt" % "process" % bootstrapSbtVersion
  lazy val ivyProj = "org.scala-sbt" % "ivy" % "0.13.10-SNAPSHOT" exclude("org.scala-sbt", "io")

  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0-M1"

  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

  lazy val sbinary = "org.scala-tools.sbinary" %% "sbinary" % "0.4.2"
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  lazy val specs2 = "org.specs2" %% "specs2" % "2.3.11"
  lazy val junit = "junit" % "junit" % "4.11"
  def testDependencies = Seq(libraryDependencies :=
    Seq(
      scalaCheck % Test,
      specs2 % Test,
      junit % Test
    ))
}
