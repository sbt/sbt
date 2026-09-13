val scala3 = "3.3.3"
val scala212 = "2.12.21"

organization := "com.example"
version := "0.1.0-SNAPSHOT"

lazy val plugin = (projectMatrix in file("plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "com.example",
    name := "sbt-example",
  )
  .jvmPlatform(scalaVersions = Seq(scala3, scala212))

publishMavenStyle := true
publishTo := Some(Resolver.file("test-publish", (ThisBuild / baseDirectory).value / "repo/"))
