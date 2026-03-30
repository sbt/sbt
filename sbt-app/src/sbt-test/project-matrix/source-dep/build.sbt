// https://github.com/sbt/sbt/issues/8971
ThisBuild / scalaVersion := "3.8.3"

lazy val extLib = ProjectRef(file("ext/lib"), "lib")

lazy val root = (project in file("."))
  .settings(
    name := "source-dep-matrix",
  )
  .dependsOn(extLib)
