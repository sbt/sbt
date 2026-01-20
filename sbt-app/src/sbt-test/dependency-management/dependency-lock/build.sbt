ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "dependency-lock-test",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.10.0"
  )
