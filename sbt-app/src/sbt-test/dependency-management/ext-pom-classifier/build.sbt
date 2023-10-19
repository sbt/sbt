ThisBuild / useCoursier := false

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.12.18",
    externalPom()
  )
