import Dependencies._

def internalPath   = file("internal")

// ThisBuild settings take lower precedence,
// but can be shared across the multi projects.
def buildLevelSettings: Seq[Setting[_]] = Seq(
  organization in ThisBuild := "org.scala-sbt.librarymanagement",
  version in ThisBuild := "1.0.0-SNAPSHOT"
  // bintrayOrganization in ThisBuild := Some("sbt"),
  // // bintrayRepository in ThisBuild := s"ivy-${(publishStatus in ThisBuild).value}",
  // bintrayPackage in ThisBuild := "sbt",
  // bintrayReleaseOnPublish in ThisBuild := false
)

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := "2.10.5",
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-target", "6", "-source", "6", "-Xlint", "-Xlint:-serial"),
  incOptions := incOptions.value.withNameHashing(true)
  // crossScalaVersions := Seq(scala210)
  // bintrayPackage := (bintrayPackage in ThisBuild).value,
  // bintrayRepository := (bintrayRepository in ThisBuild).value
)

lazy val root = (project in file(".")).
  aggregate(lm).
  settings(
    buildLevelSettings,
    commonSettings,
    // rootSettings,
    publish := {},
    publishLocal := {}
  )

lazy val lm = (project in file("librarymanagement")).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(utilLogging % "compile;test->test", ioProj % "compile;test->test", utilCollection),
    libraryDependencies ++= Seq(ivy, jsch, sbtSerialization, scalaReflect.value, launcherInterface),
    name := "librarymanagement"
  )
