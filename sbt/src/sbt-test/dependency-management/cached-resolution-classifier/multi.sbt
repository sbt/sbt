lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    scalaVersion := "2.10.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

lazy val classifierTest = project.
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(
      "net.sf.json-lib" % "json-lib" % "2.4" classifier "jdk15" intransitive()
    )
  )

lazy val transitiveTest = project.
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(
      "junit" % "junit" % "4.11" % "test"
    )
  )

lazy val a = project.
  dependsOn(classifierTest, transitiveTest % "test->test").
  settings(commonSettings: _*).
  settings(
    updateOptions := updateOptions.value.withCachedResolution(true),
    artifact in (Compile, packageBin) := Artifact("demo"),
    libraryDependencies := Seq(
      "com.typesafe.akka" %% "akka-remote" % "2.3.4" exclude("com.typesafe.akka", "akka-actor_2.10"),
      "net.databinder" %% "unfiltered-uploads" % "0.8.0",
      "commons-io" % "commons-io" % "1.3",
      "com.typesafe" % "config" % "0.4.9-SNAPSHOT"
    )
  )

lazy val b = project.
  dependsOn(classifierTest, transitiveTest % "test->test").
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(
      "com.typesafe.akka" %% "akka-remote" % "2.3.4" exclude("com.typesafe.akka", "akka-actor_2.10"),
      "net.databinder" %% "unfiltered-uploads" % "0.8.0",
      "commons-io" % "commons-io" % "1.3",
      "com.typesafe" % "config" % "0.4.9-SNAPSHOT"
    ) 
  )

lazy val c = project.
  dependsOn(a).
  settings(commonSettings: _*).
  settings(
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

lazy val root = (project in file(".")).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      val acp = (externalDependencyClasspath in Compile in a).value.sortBy {_.data.getName}
      val bcp = (externalDependencyClasspath in Compile in b).value.sortBy {_.data.getName}
      val ccp = (externalDependencyClasspath in Compile in c).value.sortBy {_.data.getName} filterNot { _.data.getName == "demo_2.10.jar"}
      
      val atestcp = (externalDependencyClasspath in Test in a).value.sortBy {_.data.getName}
      val btestcp = (externalDependencyClasspath in Test in b).value.sortBy {_.data.getName}
      val ctestcp = (externalDependencyClasspath in Test in c).value.sortBy {_.data.getName} filterNot { _.data.getName == "demo_2.10.jar"}
      if (ctestcp exists { _.data.getName contains "junit-4.11.jar" }) {
        sys.error("junit found when it should be excluded: " + ctestcp.toString)
      }
      if (acp == bcp && acp == ccp) ()
      else sys.error("Different classpaths are found:" +
        "\n - a (cached)        " + acp.toString +
        "\n - b (plain)         " + bcp.toString +
        "\n - c (inter-project) " + ccp.toString)

      if (atestcp == btestcp) ()
      else sys.error("Different classpaths are found:" +
        "\n - a test (cached) " + atestcp.toString +
        "\n - b test (plain)  " + btestcp.toString)
    }
  )
