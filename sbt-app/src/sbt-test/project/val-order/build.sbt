ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"

lazy val check = taskKey[Unit]("")
lazy val intTask = taskKey[Int]("int task")

lazy val root = (project in file("."))
  .settings(
    name := "val-ordering-test",
    ThisBuild / organization := "root",
    check := {
      val o = (ThisBuild / organization).value
      assert(o == "root.api.database.web", s"$o")
      val x = (api / intTask).value
      assert(x == 1, x.toString)

    },
  )

lazy val api = project
  .settings(
    intTask := 0,
    ThisBuild / organization := (ThisBuild / organization).value + ".api",
  )

lazy val database = project
  .settings(
    ThisBuild / organization := (ThisBuild / organization).value + ".database",
  )

lazy val web = project
  .dependsOn(api, database)
  .settings(
    api / intTask := 1,
    ThisBuild / organization := (ThisBuild / organization).value + ".web",
  )
