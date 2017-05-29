lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    libraryDependencies := Seq(
      "net.databinder" %% "unfiltered-uploads" % "0.8.0",
      "commons-io" % "commons-io" % "1.3",
      "org.scala-refactoring" %% "org.scala-refactoring.library" % "0.6.2",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value
    ),
    scalaVersion := "2.11.2",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

def consolidatedResolutionSettings: Seq[Def.Setting[_]] =
  commonSettings ++ Seq(
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

// overrides cached
lazy val a = project.
  settings(consolidatedResolutionSettings: _*).
  settings(
    dependencyOverrides += "commons-io" % "commons-io" % "2.0"
  )

// overrides plain
lazy val b = project.
  settings(commonSettings: _*).
  settings(
    dependencyOverrides += "commons-io" % "commons-io" % "2.0"
  )

lazy val root = (project in file(".")).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      val acp = (externalDependencyClasspath in Compile in a).value.sortBy {_.data.getName}
      val bcp = (externalDependencyClasspath in Compile in b).value.sortBy {_.data.getName}
      if (acp == bcp) ()
      else sys.error("Different classpaths are found:" +
        "\n - a (overrides + cached) " + acp.toString +
        "\n - b (overrides + plain)  " + bcp.toString) 
    }
  )
