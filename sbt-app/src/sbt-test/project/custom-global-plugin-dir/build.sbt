scalaVersion := "2.13.18"

lazy val root = (project in file("."))
  .dependsOn(buildA)

lazy val buildA = RootProject(file("./buildA"))