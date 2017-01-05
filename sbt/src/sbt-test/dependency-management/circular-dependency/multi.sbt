lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
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
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0-SNAPSHOT"
  )
