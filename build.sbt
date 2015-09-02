import Dependencies._

def internalPath   = file("internal")

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := "2.10.5",
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-target", "6", "-source", "6", "-Xlint", "-Xlint:-serial"),
  incOptions := incOptions.value.withNameHashing(true),
  // crossScalaVersions := Seq(scala210)
  // bintrayPackage := (bintrayPackage in ThisBuild).value,
  // bintrayRepository := (bintrayRepository in ThisBuild).value,
  resolvers += Resolver.sonatypeRepo("public")
)

lazy val root = (project in file(".")).
  aggregate(lm).
  settings(
    inThisBuild(Seq(
      organization := "org.scala-sbt",
      version := "0.1.0-SNAPSHOT",
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
      bintrayReleaseOnPublish := false,
      bintrayOrganization := Some("sbt"),
      bintrayRepository := "maven-releases",
      bintrayPackage := "librarymanagement"
    )),
    commonSettings,
    // rootSettings,
    publish := {},
    publishLocal := {}
  )

lazy val lm = (project in file("librarymanagement")).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(utilLogging, ioProj, utilCollection),
    libraryDependencies ++= Seq(ivy, jsch, sbtSerialization, scalaReflect.value, launcherInterface),
    name := "librarymanagement"
  )
