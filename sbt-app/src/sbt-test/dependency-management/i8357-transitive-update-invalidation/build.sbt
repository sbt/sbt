// Test for https://github.com/sbt/sbt/issues/8357
// Verifies that transitiveUpdate correctly invalidates across command invocations
// when a dependency's dependencies change.

ThisBuild / scalaVersion := "2.12.21"

// Use a setting to control library version - this can be changed via reload
lazy val catsVersion = settingKey[String]("Cats version")

// Track the stamp from our own update to verify invalidation
lazy val ourStamp = taskKey[String]("Our update's stamp")
// Track the max stamp from transitive dependencies
lazy val maxDepStamp = taskKey[String]("Max stamp from transitive deps")

lazy val a = project.in(file("a"))
  .settings(
    catsVersion := "2.8.0",
    libraryDependencies += "org.typelevel" %% "cats-core" % catsVersion.value,
  )

lazy val itTests = project.in(file("itTests"))
  .dependsOn(a % "test->test")
  .settings(
    // Get our update's stamp
    ourStamp := update.value.stats.stamp.getOrElse(""),

    // Get the max stamp from transitive dependencies
    maxDepStamp := transitiveUpdate.value.flatMap(_.stats.stamp).maxOption.getOrElse(""),
  )
