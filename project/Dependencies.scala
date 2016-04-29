import sbt._
import Keys._

object Dependencies {
  lazy val scala282 = "2.8.2"
  lazy val scala292 = "2.9.2"
  lazy val scala293 = "2.9.3"
  lazy val scala210 = "2.10.6"
  lazy val scala211 = "2.11.8"

  // sbt modules
  val utilVersion = "0.1.0-M10"
  val ioVersion = "1.0.0-M3"
  val incrementalcompilerVersion = "0.1.0-M3"
  val librarymanagementVersion = "0.1.0-M7"
  lazy val sbtIO = "org.scala-sbt" %% "io" % ioVersion
  lazy val utilCollection = "org.scala-sbt" %% "util-collection" % utilVersion
  lazy val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  lazy val utilTesting = "org.scala-sbt" %% "util-testing" % utilVersion
  lazy val utilControl = "org.scala-sbt" %% "util-control" % utilVersion
  lazy val utilCompletion = "org.scala-sbt" %% "util-completion" % utilVersion
  lazy val utilApplyMacro = "org.scala-sbt" %% "util-apply-macro" % utilVersion
  lazy val utilRelation = "org.scala-sbt" %% "util-relation" % utilVersion
  lazy val utilLogic = "org.scala-sbt" %% "util-logic" % utilVersion
  lazy val utilTracking = "org.scala-sbt" %% "util-tracking" % utilVersion
  lazy val utilScripted = "org.scala-sbt" %% "util-scripted" % utilVersion
  lazy val libraryManagement = "org.scala-sbt" %% "librarymanagement" % librarymanagementVersion
  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0-M1"
  lazy val rawLauncher = "org.scala-sbt" % "launcher" % "1.0.0-M1"
  lazy val testInterface = "org.scala-sbt" % "test-interface" % "1.0"

  lazy val incrementalcompiler = "org.scala-sbt" %% "incrementalcompiler" % incrementalcompilerVersion
  lazy val incrementalcompilerCompile = "org.scala-sbt" %% "incrementalcompiler-compile" % incrementalcompilerVersion
  lazy val compilerInterface = "org.scala-sbt" % "compiler-interface" % incrementalcompilerVersion
  lazy val compilerBrdige = "org.scala-sbt" %% "compiler-bridge" % incrementalcompilerVersion
  lazy val compilerClasspath = "org.scala-sbt" %% "incrementalcompiler-classpath" % incrementalcompilerVersion
  lazy val compilerApiInfo = "org.scala-sbt" %% "incrementalcompiler-apiinfo" % incrementalcompilerVersion
  lazy val compilerIvyIntegration = "org.scala-sbt" %% "incrementalcompiler-ivy-integration" % incrementalcompilerVersion

  lazy val jline = "jline" % "jline" % "2.13"
  lazy val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-2cc8d2761242b072cedb0a04cb39435c4fa24f9a"
  lazy val jsch = "com.jcraft" % "jsch" % "0.1.46" intransitive ()
  lazy val sbinary = "org.scala-tools.sbinary" %% "sbinary" % "0.4.2"
  lazy val sbtSerialization = "org.scala-sbt" %% "serialization" % "0.1.2"
  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }
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
