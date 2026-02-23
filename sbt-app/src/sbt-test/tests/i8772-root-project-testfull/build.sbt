ThisBuild / scalaVersion := "2.13.18"

lazy val a = project

lazy val b = project
  .in(file("."))
  .settings(
    libraryDependencies += "org.scalatest" %% "scalatest-funsuite" % "3.2.19" % Test
  )
  .dependsOn(a)
  .aggregate(a)
