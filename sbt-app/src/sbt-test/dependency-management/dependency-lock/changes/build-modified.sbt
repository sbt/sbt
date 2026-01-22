ThisBuild / scalaVersion := "2.13.12"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

lazy val root = (project in file("."))
  .settings(
    name := "dependency-lock-test",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.10.0",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % Test
  )
