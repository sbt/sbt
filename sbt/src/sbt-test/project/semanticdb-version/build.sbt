ThisBuild / scalaVersion := "2.12.13"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := "4.4.10"
ThisBuild / semanticdbIncludeInJar := false

lazy val root = (project in file("."))

lazy val check = taskKey[Unit]("Checks the configured semanticdbVersion")

check := {
  val expected = Some("4.4.10")
  val actual = allDependencies
    .value
    .find(_.name == "semanticdb-scalac")
    .map(_.revision)

  assert(actual == expected, s"Expected version to be $expected, was $actual")
}
