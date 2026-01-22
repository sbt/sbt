ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

val a = taskKey[Unit]("")
val b = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    name := "sbt-file-outputs-test",
    a / fileOutputs += (baseDirectory.value / "a-output").toGlob,
    a := {
      ()
    },
    b := {
      a.outputFileChanges
    }
  )