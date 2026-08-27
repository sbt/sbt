ThisBuild / scalaVersion := "3.9.0"

lazy val lib = (projectMatrix in file("."))
  .settings(
    name := "lib",
  )
  .jvmPlatform(scalaVersions = Seq("3.9.0"))
