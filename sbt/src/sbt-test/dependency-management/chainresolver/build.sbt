lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
    scalaVersion in ThisBuild := "2.11.7",
    organization in ThisBuild := "com.example",
    version in ThisBuild := "0.1.0-SNAPSHOT",
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
