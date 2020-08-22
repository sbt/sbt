val dottyVersion = "0.23.0"
ThisBuild / resolvers += "scala-integration" at "https://scala-ci.typesafe.com/artifactory/scala-integration/"
// TODO use 2.13.4 when it's out
lazy val scala213 = "2.13.4-bin-aeee8f0"

lazy val fooApp = (projectMatrix in file("foo-app"))
  .dependsOn(fooCore)
  .settings(
    name := "app",
  )
  .jvmPlatform(scalaVersions = Seq(dottyVersion))

lazy val fooCore = (projectMatrix in file("foo-core"))
  .settings(
    name := "core",
  )
  .jvmPlatform(scalaVersions = Seq("2.13.3", "2.12.12"))
