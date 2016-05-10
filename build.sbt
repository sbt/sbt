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
  scalacOptions ++= Seq(
    "-encoding", "utf8",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-Xfuture",
    "-Yinline-warnings",
    "-Xfatal-warnings",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"),
  previousArtifact := None, // Some(organization.value %% moduleName.value % "1.0.0"),
  publishArtifact in Compile := true,
  publishArtifact in Test := true
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
    libraryDependencies ++= Seq(
      utilLogging.artifacts(
        Artifact("util-logging", "jar", "jar", classifier = None, configurations = List(Compile), url = None),
        Artifact("util-logging", "jar", "jar", classifier = Some("tests"), configurations = List(Test), url = None)
      ),
      sbtIO.artifacts(
        Artifact("io", "jar", "jar", classifier = None, configurations = List(Compile), url = None),
        Artifact("io", "jar", "jar", classifier = Some("tests"), configurations = List(Test), url = None)
      ),
      utilTesting % Test,
      utilCollection, utilCompletion, ivy, jsch, sbtSerialization, scalaReflect.value, launcherInterface),
    resourceGenerators in Compile <+= (version, resourceManaged, streams, compile in Compile) map Util.generateVersionFile,
    name := "librarymanagement",
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
