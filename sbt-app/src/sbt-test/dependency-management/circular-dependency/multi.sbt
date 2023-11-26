lazy val check = taskKey[Unit]("Runs the check")

ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    localCache,
    scalaVersion := "2.10.4",
    fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project")
  )

lazy val a = project.
  settings(commonSettings: _*).
  settings(
    name := "a",
    libraryDependencies := Seq(
      "commons-io" % "commons-io" % "1.3"
    )
  )

lazy val b = project.
  settings(commonSettings: _*).
  settings(
    name := "b"
  )

lazy val c = project.
  settings(commonSettings: _*).
  settings(
    name := "c",
    libraryDependencies := Seq(organization.value %% "b" % version.value)
  )

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    ThisBuild / organization := "org.example",
    ThisBuild / version := "1.0-SNAPSHOT",
  )
