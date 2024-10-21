ThisBuild / scalaVersion := "2.12.20"

val dependency = project.settings(exportJars := true)
val descendant = project
  .dependsOn(dependency)
  .settings(
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
