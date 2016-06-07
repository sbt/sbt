import sbt._
import Keys._

object Dependencies {
  lazy val scala282 = "2.8.2"
  lazy val scala292 = "2.9.2"
  lazy val scala293 = "2.9.3"
  lazy val scala210 = "2.10.6"
  lazy val scala211 = "2.11.8"

  // sbt modules
  val ioVersion = "1.0.0-M6"
  val utilVersion = "0.1.0-M13"
  val librarymanagementVersion = "0.1.0-M12"
  val zincVersion = "1.0.0-M3"
  lazy val sbtIO = "org.scala-sbt" %% "io" % ioVersion
  lazy val utilCollection = "org.scala-sbt" %% "util-collection" % utilVersion
  lazy val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  lazy val utilTesting = "org.scala-sbt" %% "util-testing" % utilVersion
  lazy val utilControl = "org.scala-sbt" %% "util-control" % utilVersion
  lazy val utilCompletion = "org.scala-sbt" %% "util-completion" % utilVersion
  lazy val utilApplyMacro = "org.scala-sbt" %% "util-apply-macro" % utilVersion
  lazy val utilRelation = "org.scala-sbt" %% "util-relation" % utilVersion
  lazy val utilLogic = "org.scala-sbt" %% "util-logic" % utilVersion
  lazy val utilCache = "org.scala-sbt" %% "util-cache" % utilVersion
  lazy val utilTracking = "org.scala-sbt" %% "util-tracking" % utilVersion
  lazy val utilScripted = "org.scala-sbt" %% "util-scripted" % utilVersion
  lazy val libraryManagement = "org.scala-sbt" %% "librarymanagement" % librarymanagementVersion
  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"
  lazy val rawLauncher = "org.scala-sbt" % "launcher" % "1.0.0"
  lazy val testInterface = "org.scala-sbt" % "test-interface" % "1.0"

  lazy val zinc = "org.scala-sbt" %% "zinc" % zincVersion
  lazy val zincCompile = "org.scala-sbt" %% "zinc-compile" % zincVersion
  lazy val compilerInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  lazy val compilerBrdige = "org.scala-sbt" %% "compiler-bridge" % zincVersion
  lazy val compilerClasspath = "org.scala-sbt" %% "zinc-classpath" % zincVersion
  lazy val compilerApiInfo = "org.scala-sbt" %% "zinc-apiinfo" % zincVersion
  lazy val compilerIvyIntegration = "org.scala-sbt" %% "zinc-ivy-integration" % zincVersion

  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  lazy val specs2 = "org.specs2" %% "specs2" % "2.3.11"
  lazy val junit = "junit" % "junit" % "4.11"

  private def scala211Module(name: String, moduleVersion: String) =
    Def.setting {
      scalaVersion.value match {
        case sv if (sv startsWith "2.9.") || (sv startsWith "2.10.") => Nil
        case _ => ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
      }
    }
  lazy val scalaXml = scala211Module("scala-xml", "1.0.1")
  lazy val scalaParsers = scala211Module("scala-parser-combinators", "1.0.1")
}
