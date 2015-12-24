import sbt._
import Keys._

object Dependencies {

  val scala282 = "2.8.2"
  val scala293 = "2.9.3"
  val scala210 = "2.10.6"
  val scala211 = "2.11.7"

  val bootstrapSbtVersion = "0.13.8"
  val utilVersion = "0.1.0-M8"

  lazy val sbtIO = "org.scala-sbt" %% "io" % "1.0.0-M3"
  lazy val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  lazy val utilControl = "org.scala-sbt" %% "util-control" % utilVersion
  lazy val utilRelation = "org.scala-sbt" %% "util-relation" % utilVersion
  lazy val utilTesting = "org.scala-sbt" %% "util-testing" % utilVersion
  lazy val utilTracking = "org.scala-sbt" %% "util-tracking" % utilVersion
  lazy val utilInterface = "org.scala-sbt" % "util-interface" % utilVersion
  lazy val libraryManagement = "org.scala-sbt" %% "librarymanagement" % "0.1.0-M6"
  lazy val utilScripted = "org.scala-sbt" %% "util-scripted" % utilVersion

  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0-M1"

  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

  lazy val sbinary = "org.scala-tools.sbinary" %% "sbinary" % "0.4.2"
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  lazy val scalatest = "org.scalatest" %% "scalatest" % "2.2.4"
  lazy val specs2 = "org.specs2" %% "specs2" % "2.3.11"
  lazy val junit = "junit" % "junit" % "4.11"
  def testDependencies = Seq(libraryDependencies :=
    Seq(
      utilTesting % Test,
      scalaCheck % Test,
      scalatest % Test,
      junit % Test
    ))
}
