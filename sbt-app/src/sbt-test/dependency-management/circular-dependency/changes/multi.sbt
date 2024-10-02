ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

lazy val check = taskKey[Unit]("Runs the check")

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    localCache,
    scalaVersion := "2.10.4",
    fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project"),
    updateOptions := updateOptions.value.withCircularDependencyLevel(CircularDependencyLevel.Error)
  )

lazy val a = project.
  settings(commonSettings: _*).
  settings(
    name := "a",
    libraryDependencies := Seq(
      "commons-io" % "commons-io" % "1.3",
      organization.value %% "c" % version.value
    )
  )

lazy val b = project.
  settings(commonSettings: _*).
  settings(
    name := "b",
    // this adds circular dependency
    libraryDependencies := Seq(organization.value %% "c" % version.value)
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
    (ThisBuild / organization) := "org.example",
    (ThisBuild / version) := "1.0-SNAPSHOT"
  )
