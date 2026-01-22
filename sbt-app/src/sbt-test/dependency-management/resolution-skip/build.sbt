ThisBuild / scalaVersion := "2.13.12"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

lazy val root = (project in file("."))
  .settings(
    name := "resolution-skip-test",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.10.0",
    dependencyLockFile := baseDirectory.value / "deps.lock"
  )
