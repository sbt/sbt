ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "resolution-skip-test",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.10.0",
    useDependencyLock := true,
    dependencyLockFile := Some(baseDirectory.value / "dependencies.lock")
  )
