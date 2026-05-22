ThisBuild / organization := "com.example"
ThisBuild / version      := "0.1.0-SNAPSHOT"

val `scalac-options` = project.enablePlugins(SbtPlugin).settings(
  addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.4"),
)
val components = project.enablePlugins(SbtPlugin)
val plugins    = project.dependsOn(components, `scalac-options`).enablePlugins(SbtPlugin)
