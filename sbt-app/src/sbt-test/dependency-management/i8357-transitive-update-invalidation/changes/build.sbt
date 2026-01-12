// Test for https://github.com/sbt/sbt/issues/8357
// Verifies that transitiveUpdate correctly invalidates across command invocations
// when a dependency's dependencies change.

ThisBuild / scalaVersion := "2.12.21"

// Use a setting to control library version - this can be changed via reload
lazy val toolkitVersion = settingKey[String]("Toolkit version")

// Track the stamp from our own update to verify invalidation
lazy val ourStamp = taskKey[String]("Our update's stamp")
// Track the max stamp from transitive dependencies
lazy val maxDepStamp = taskKey[String]("Max stamp from transitive deps")

lazy val a = project.in(file("a"))
  .settings(
    toolkitVersion := "0.6.0",  // CHANGED from 0.5.0 to 0.6.0
    libraryDependencies += "org.scala-lang" %% "toolkit" % toolkitVersion.value,
  )

lazy val itTests = project.in(file("itTests"))
  .dependsOn(a % "test->test")
  .settings(
    // Get our update's stamp
    ourStamp := update.value.stats.stamp,

    // Get the max stamp from transitive dependencies
    maxDepStamp := transitiveUpdate.value.map(_.stats.stamp).maxOption.getOrElse(""),
  )
