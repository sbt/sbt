lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
    libraryDependencies := Seq(
      "net.sf.json-lib" % "json-lib" % "2.4" classifier "jdk15" intransitive(),
      "com.typesafe.akka" %% "akka-remote" % "2.3.4" exclude("com.typesafe.akka", "akka-actor_2.10"),
      "commons-io" % "commons-io" % "1.3"
    ),
    dependencyOverrides += "commons-io" % "commons-io" % "1.4",
    scalaVersion := "2.10.4"
  )

def consolidatedResolutionSettings: Seq[Def.Setting[_]] =
  commonSettings ++ Seq(
    updateOptions := updateOptions.value.withConsolidatedResolution(true)
  )

lazy val a = project.
  settings(consolidatedResolutionSettings: _*).
  settings(
    artifact in (Compile, packageBin) := Artifact("demo")
  )

lazy val b = project.
  settings(commonSettings: _*)

lazy val c = project.
  settings(consolidatedResolutionSettings: _*).
  settings(
    libraryDependencies := Seq(organization.value %% "a" % version.value)
  )

lazy val root = (project in file(".")).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      val acp = (externalDependencyClasspath in Compile in a).value
      val bcp = (externalDependencyClasspath in Compile in b).value
      if (acp == bcp) ()
      else sys.error("Different classpaths are found:\n" + acp.toString + "\n" + bcp.toString)
    }
  )
