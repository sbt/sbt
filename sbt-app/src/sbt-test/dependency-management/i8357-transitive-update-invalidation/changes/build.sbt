// Test for https://github.com/sbt/sbt/issues/8357
// Verifies that transitiveUpdate correctly invalidates across command invocations
// when a dependency's dependencies change.

ThisBuild / scalaVersion := "2.12.21"

// Use a setting to control library version - this can be changed via reload
lazy val toolkitVersion = settingKey[String]("Toolkit version")

// Track the resolvedAt timestamp from our own update to verify invalidation
lazy val ourResolvedAt = taskKey[Long]("Our update's resolvedAt timestamp")
// Track the max resolvedAt from transitive dependencies
lazy val maxDepResolvedAt = taskKey[Long]("Max resolvedAt from transitive deps")

lazy val a = project.in(file("a"))
  .settings(
    toolkitVersion := "0.6.0",  // CHANGED from 0.5.0 to 0.6.0
    libraryDependencies += "org.scala-lang" %% "toolkit" % toolkitVersion.value,
  )

lazy val itTests = project.in(file("itTests"))
  .dependsOn(a % "test->test")
  .settings(
    // Get our update's resolvedAt timestamp
    ourResolvedAt := update.value.stats.resolvedAt,

    // Get the max resolvedAt from transitive dependencies
    maxDepResolvedAt := transitiveUpdate.value.map(_.stats.resolvedAt).maxOption.getOrElse(0L),
  )
