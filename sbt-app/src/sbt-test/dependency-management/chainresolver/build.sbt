lazy val check = taskKey[Unit]("Runs the check")

ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    localCache,
    ThisBuild / scalaVersion := "2.11.12",
    ThisBuild / organization := "com.example",
    ThisBuild / version := "0.1.0-SNAPSHOT",
    autoScalaLibrary := false,
    crossPaths := false
  )

lazy val realCommonsIoClient = project.
  settings(
    commonSettings,
    name := "a",
    libraryDependencies := Seq(
      "commons-io" % "commons-io" % "1.3"
    ),
    fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project")
  )

lazy val fakeCommonsIo = project.
  settings(
    commonSettings,
    organization := "commons-io",
    name := "commons-io",
    version := "1.3"
  )

lazy val fakeCommonsIoClient = project.
  dependsOn(fakeCommonsIo % "test->test").
  settings(
    commonSettings,
    name := "c"
  )
