import sbt._
import Keys._

object Dependencies {
  val scala282 = "2.8.2"
  val scala292 = "2.9.2"
  val scala293 = "2.9.3"
  val scala210 = "2.10.6"
  val scala211 = "2.11.8"

  // sbt modules
  val ioVersion   = "1.0.0-M6"
  val utilVersion = "0.1.0-M14"
  val lmVersion   = "0.1.0-X1"
  val zincVersion = "1.0.0-X3"

  val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  val utilApplyMacro = "org.scala-sbt" %% "util-apply-macro" % utilVersion
  val utilCache      = "org.scala-sbt" %% "util-cache"       % utilVersion
  val utilCollection = "org.scala-sbt" %% "util-collection"  % utilVersion
  val utilCompletion = "org.scala-sbt" %% "util-completion"  % utilVersion
  val utilControl    = "org.scala-sbt" %% "util-control"     % utilVersion
  val utilLogging    = "org.scala-sbt" %% "util-logging"     % utilVersion
  val utilLogic      = "org.scala-sbt" %% "util-logic"       % utilVersion
  val utilRelation   = "org.scala-sbt" %% "util-relation"    % utilVersion
  val utilScripted   = "org.scala-sbt" %% "util-scripted"    % utilVersion
  val utilTesting    = "org.scala-sbt" %% "util-testing"     % utilVersion
  val utilTracking   = "org.scala-sbt" %% "util-tracking"    % utilVersion

  val libraryManagement = "org.scala-sbt" %% "librarymanagement" % lmVersion

  val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"
  val rawLauncher       = "org.scala-sbt" % "launcher"           % "1.0.0"
  val testInterface     = "org.scala-sbt" % "test-interface"     % "1.0"

  val compilerApiInfo        = "org.scala-sbt" %% "zinc-apiinfo"         % zincVersion
  val compilerBridge         = "org.scala-sbt" %% "compiler-bridge"      % zincVersion
  val compilerClasspath      = "org.scala-sbt" %% "zinc-classpath"       % zincVersion
  val compilerInterface      = "org.scala-sbt"  % "compiler-interface"   % zincVersion
  val compilerIvyIntegration = "org.scala-sbt" %% "zinc-ivy-integration" % zincVersion
  val zinc                   = "org.scala-sbt" %% "zinc"                 % zincVersion
  val zincCompile            = "org.scala-sbt" %% "zinc-compile"         % zincVersion

  val sjsonNewScalaJson = "com.eed3si9n" %% "sjson-new-scalajson" % "0.4.2"

  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  val specs2     = "org.specs2"     %% "specs2"     % "2.3.11"
  val junit      = "junit"           % "junit"      % "4.11"

  private def scala211Module(name: String, moduleVersion: String) = Def setting (
    scalaBinaryVersion.value match {
      case "2.9" | "2.10" => Nil
      case _              => ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
    }
  )

  val scalaXml     = scala211Module("scala-xml", "1.0.1")
  val scalaParsers = scala211Module("scala-parser-combinators", "1.0.1")
}
