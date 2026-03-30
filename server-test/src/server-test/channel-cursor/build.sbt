scalaVersion := "3.8.3"

val printCurrentProject = inputKey[Unit]("Prints current project name")

lazy val projectA = (project in file("projectA"))
  .settings(
    name := "project-a",
    printCurrentProject := {
      streams.value.log.info(s"CURRENT_PROJECT_IS:${name.value}")
    }
  )

lazy val projectB = (project in file("projectB"))
  .settings(
    name := "project-b",
    printCurrentProject := {
      streams.value.log.info(s"CURRENT_PROJECT_IS:${name.value}")
    }
  )

lazy val root = (project in file("."))
  .aggregate(projectA, projectB)
  .settings(
    name := "root",
    printCurrentProject := {
      streams.value.log.info(s"CURRENT_PROJECT_IS:${name.value}")
    }
  )
