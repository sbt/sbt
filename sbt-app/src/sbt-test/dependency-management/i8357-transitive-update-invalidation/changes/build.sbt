// Test for https://github.com/sbt/sbt/issues/8357
// This is the changed build.sbt with different library version

ThisBuild / scalaVersion := "2.12.21"

lazy val catsVersion = settingKey[String]("Cats version")
lazy val ourStamp = taskKey[String]("Our update's stamp")
lazy val maxDepStamp = taskKey[String]("Max stamp from transitive deps")

lazy val a = project.in(file("a"))
  .settings(
    // Changed from 2.8.0 to 2.9.0
    catsVersion := "2.9.0",
    libraryDependencies += "org.typelevel" %% "cats-core" % catsVersion.value,
  )

lazy val itTests = project.in(file("itTests"))
  .dependsOn(a % "test->test")
  .settings(
    ourStamp := update.value.stats.stamp.getOrElse(""),
    maxDepStamp := transitiveUpdate.value.flatMap(_.stats.stamp).maxOption.getOrElse(""),
  )
