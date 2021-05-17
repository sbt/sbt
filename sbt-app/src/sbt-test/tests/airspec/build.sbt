ThisBuild / scalaVersion := "2.12.13"

lazy val airspec = "org.wvlet.airframe" %% "airspec" % "21.5.4"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies += airspec % Test
  )
