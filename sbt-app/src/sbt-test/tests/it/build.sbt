ThisBuild / scalaVersion := "2.12.20"

val specs = "org.specs2" %% "specs2-core" % "4.3.4"

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    libraryDependencies += specs % IntegrationTest
  )
