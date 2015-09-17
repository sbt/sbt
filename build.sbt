import Dependencies._

def baseVersion = "0.1.0"
def internalPath   = file("internal")

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := "2.10.5",
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
  publishArtifact in Compile := true,
  publishArtifact in Test := true,
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
    "-Ywarn-value-discard")
)

lazy val root = (project in file(".")).
  aggregate(lm).
  settings(
    inThisBuild(Seq(
      organization := "org.scala-sbt",
      homepage := Some(url("https://github.com/sbt/librarymanagement")),
      description := "Library management module for sbt",
      licenses := List("BSD New" -> url("https://github.com/sbt/sbt/blob/0.13/LICENSE")),
      scmInfo := Some(ScmInfo(url("https://github.com/sbt/librarymanagement"), "git@github.com:sbt/librarymanagement.git")),
      developers := List(
        Developer("harrah", "Mark Harrah", "@harrah", url("https://github.com/harrah")),
        Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", url("https://github.com/eed3si9n")),
        Developer("jsuereth", "Josh Suereth", "@jsuereth", url("https://github.com/jsuereth")),
        Developer("dwijnand", "Dale Wijnand", "@dwijnand", url("https://github.com/dwijnand"))
      ),
      bintrayPackage := "librarymanagement",
      git.baseVersion := baseVersion
    )),
    commonSettings,
    name := "LM Root",
    publish := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publishArtifact in Test := false,
    publishArtifact := false
  )

lazy val lm = (project in file("librarymanagement")).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      utilLogging, (utilLogging % Test).classifier("tests"),
      sbtIO, (sbtIO % Test).classifier("tests"),
      utilTesting % Test,
      utilCollection, ivy, jsch, sbtSerialization, scalaReflect.value, launcherInterface),
    resourceGenerators in Compile <+= (version, resourceManaged, streams, compile in Compile) map Util.generateVersionFile,
    name := "librarymanagement"
  )
