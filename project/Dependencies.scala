import sbt._
import Keys._

object Dependencies {
  lazy val scala282 = "2.8.2"
  lazy val scala292 = "2.9.2"
  lazy val scala293 = "2.9.3"
  lazy val scala210 = "2.10.6"
  lazy val scala211 = "2.11.7"

  // sbt modules
  val utilVersion = "0.1.0-M8"
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
  lazy val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-39ece6f17e1af62c2e89a9d88b59c0c3aad56145"
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

  // Maven related dependnecy craziness
  //val mvnEmbedder = "org.apache.maven" % "maven-embedder" % mvnVersion
  val mvnWagonVersion = "2.4"
  val mvnVersion = "3.2.3"
  val aetherVersion = "1.0.1.v20141111"

  val mvnAether = "org.apache.maven" % "maven-aether-provider" % mvnVersion
  val aether = "org.eclipse.aether" % "aether" % aetherVersion
  val aetherImpl = "org.eclipse.aether" % "aether-impl" % aetherVersion
  val aetherUtil = "org.eclipse.aether" % "aether-util" % aetherVersion
  val aetherTransportFile = "org.eclipse.aether" % "aether-transport-file" % aetherVersion
  val aetherTransportWagon = "org.eclipse.aether" % "aether-transport-wagon" % aetherVersion
  val aetherTransportHttp = "org.eclipse.aether" % "aether-transport-http" % aetherVersion
  val aetherConnectorBasic = "org.eclipse.aether" % "aether-connector-basic" % aetherVersion
  val sisuPlexus = ("org.eclipse.sisu" % "org.eclipse.sisu.plexus" % "0.3.0.M1").exclude("javax.enterprise", "cdi-api").exclude("com.google.code.findbugs", "jsr305")
  val guice = "com.google.inject" % "guice" % "3.0"
  val guava = "com.google.guava" % "guava" % "18.0"
  val javaxInject = "javax.inject" % "javax.inject" % "1"

  //val sisuGuice = ("org.eclipse.sisu" % "sisu-guice" % "3.1.0").classifier("no_aop").exclude("javax.enterprise", "cdi-api", )

  /*
  val mvnWagon = "org.apache.maven.wagon" % "wagon-http" % mvnWagonVersion
  val mvnWagonProviderApi = "org.apache.maven.wagon" % "wagon-provider-api" % mvnWagonVersion
  val mvnWagonLwHttp = "org.apache.maven.wagon" % "wagon-http-lightweight" % mvnWagonVersion
  val mvnWagonFile = "org.apache.maven.wagon" % "wagon-file" % mvnWagonVersion
  */
  def aetherLibs =
    Seq(
      guava,
      javaxInject,
      sisuPlexus,
      aetherImpl,
      aetherConnectorBasic,
      mvnAether)

}
