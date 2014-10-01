lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
    libraryDependencies := Seq(
      "net.sf.json-lib" % "json-lib" % "2.4" classifier "jdk15" intransitive(),
      "com.typesafe.akka" %% "akka-remote" % "2.3.4" exclude("com.typesafe.akka", "akka-actor_2.10"),
      "net.databinder" %% "unfiltered-uploads" % "0.8.0",
      "commons-io" % "commons-io" % "1.3",
      "com.typesafe" % "config" % "0.4.9-SNAPSHOT"
    ),
    // dependencyOverrides += "commons-io" % "commons-io" % "1.4",
    scalaVersion := "2.10.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
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
  dependsOn(a).
  settings(consolidatedResolutionSettings: _*).
  settings(
    // libraryDependencies := Seq(organization.value %% "a" % version.value)
  )

lazy val root = (project in file(".")).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      val acp = (externalDependencyClasspath in Compile in a).value.sortBy {_.data.getName}
      val bcp = (externalDependencyClasspath in Compile in b).value.sortBy {_.data.getName}
      val ccp = (externalDependencyClasspath in Compile in c).value.sortBy {_.data.getName} filterNot {_.data.getName == "demo_2.10.jar"}
      if (acp == bcp && acp == ccp) ()
      else sys.error("Different classpaths are found:" +
        "\n - a (consolidated)  " + acp.toString +
        "\n - b (plain)         " + bcp.toString +
        "\n - c (inter-project) " + ccp.toString) 
    }
  )
