ThisBuild / scalaVersion := "2.12.20"

lazy val munit = "org.scalameta" %% "munit" % "0.7.22"

lazy val root = (project in file("."))
  .settings(
    Compile / scalacOptions += "-Yrangepos",
    libraryDependencies += munit % Test
  )
