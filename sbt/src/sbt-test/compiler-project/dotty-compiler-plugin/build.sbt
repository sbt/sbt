// hardcode dottyVersion to make test deterministic
lazy val dottyVersion = "0.8.0-bin-20180424-e77604d-NIGHTLY"

lazy val plugin = (project in file("plugin"))
  .settings(
    name := "dividezero",
    version := "0.0.1",
    organization := "ch.epfl.lamp",
    scalaVersion := dottyVersion,
  )

lazy val app = (project in file("."))
  .settings(
    scalaVersion := dottyVersion,
    libraryDependencies += compilerPlugin("ch.epfl.lamp" %% "dividezero" % "0.0.1"),
  )
