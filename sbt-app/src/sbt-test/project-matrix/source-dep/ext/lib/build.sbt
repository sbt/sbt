ThisBuild / scalaVersion := "3.8.2"

lazy val lib = (projectMatrix in file("."))
  .settings(
    name := "lib",
  )
  .jvmPlatform(scalaVersions = Seq("3.8.2"))
