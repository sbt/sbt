import Dependencies._
import com.typesafe.tools.mima.core._, ProblemFilters._

def baseVersion = "0.1.0"
def internalPath   = file("internal")

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := scala211,
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.bintrayRepo("sbt", "maven-releases"),
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-target", "6", "-source", "6", "-Xlint", "-Xlint:-serial"),
  incOptions := incOptions.value.withNameHashing(true),
  crossScalaVersions := Seq(scala210, scala211),
  resolvers += Resolver.sonatypeRepo("public"),
  scalacOptions += "-Xfatal-warnings",
  previousArtifact := None, // Some(organization.value %% moduleName.value % "1.0.0"),
  publishArtifact in Compile := true,
  publishArtifact in Test := false
)

lazy val root = (project in file(".")).
  aggregate(lm).
  settings(
    inThisBuild(Seq(
      homepage := Some(url("https://github.com/sbt/librarymanagement")),
      description := "Library management module for sbt",
      scmInfo := Some(ScmInfo(url("https://github.com/sbt/librarymanagement"), "git@github.com:sbt/librarymanagement.git")),
      bintrayPackage := "librarymanagement",
      git.baseVersion := baseVersion
    )),
    commonSettings,
    name := "LM Root",
    publish := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publishArtifact in Test := false,
    publishArtifact := false,
    customCommands
  )

lazy val lm = (project in file("librarymanagement")).
  settings(
    commonSettings,
    name := "librarymanagement",
    libraryDependencies ++= Seq(
      utilLogging, sbtIO, utilTesting % Test,
      utilCollection, utilCompletion, ivy, jsch, sbtSerialization, scalaReflect.value, launcherInterface),
    resourceGenerators in Compile <+= (version, resourceManaged, streams, compile in Compile) map Util.generateVersionFile,
    publishArtifact in Test := false,
    binaryIssueFilters ++= Seq()
  )

def customCommands: Seq[Setting[_]] = Seq(
  commands += Command.command("release") { state =>
    // "clean" ::
    "so compile" ::
    "so publishSigned" ::
    "reload" ::
    state
  }
)
