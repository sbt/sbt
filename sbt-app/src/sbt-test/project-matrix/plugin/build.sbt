val scala3 = "3.3.3"
val scala212 = "2.12.19"

organization := "com.example"
version := "0.1.0-SNAPSHOT"

lazy val checkSbt2Plugin = taskKey[Unit]("")

lazy val plugin = (projectMatrix in file("plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "com.example",
    name := "sbt-example",
    // TODO: Once 2.0 is released we can move this check to the `test` file
    checkSbt2Plugin := {
      val repo = (ThisBuild / baseDirectory).value / "repo"
      val sbtV = sbtBinaryVersion.value
      val expected = (repo / "com" / "example" /
        s"sbt-example_sbt${sbtV}_3" /
        "0.1.0-SNAPSHOT" /
        s"sbt-example_sbt${sbtV}_3-0.1.0-SNAPSHOT.pom")
      assert(expected.exists, s"$expected did not exist")
    },
  )
  .jvmPlatform(scalaVersions = Seq(scala3, scala212))

publishMavenStyle := true
publishTo := Some(Resolver.file("test-publish", (ThisBuild / baseDirectory).value / "repo/"))
