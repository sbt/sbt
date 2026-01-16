lazy val `project-runtime` = (project in file(".")).
  dependsOn(projectGenerator % "optional").
  settings(
    name := "project-runtime",
    scalaVersion := "2.11.7",
    crossPaths := false
  )

lazy val projectGenerator = ProjectRef(uri("generator/"), "project")
