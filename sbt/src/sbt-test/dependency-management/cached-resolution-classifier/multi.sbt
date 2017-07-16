lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    scalaVersion := "2.10.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

lazy val classifierTest = project.
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(
      "net.sf.json-lib" % "json-lib" % "2.4" classifier "jdk15" intransitive(),
      "commons-io" % "commons-io" % "1.4"
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
      "commons-io" % "commons-io" % "1.4" classifier "sources",
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
      "commons-io" % "commons-io" % "1.4" classifier "sources",
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
      val acp = (externalDependencyClasspath in Compile in a).value.map {_.data.getName}.sorted
      val bcp = (externalDependencyClasspath in Compile in b).value.map {_.data.getName}.sorted
      val ccp = (externalDependencyClasspath in Compile in c).value.map {_.data.getName}.sorted filterNot { _ == "demo_2.10.jar"}
      if (!(acp contains "commons-io-1.4-sources.jar")) {
        sys.error("commons-io-1.4-sources not found when it should be included: " + acp.toString)
      }
      // if (!(acp contains "commons-io-1.4.jar")) {
      //   sys.error("commons-io-1.4 not found when it should be included: " + acp.toString)
      // }
      
      // stock Ivy implementation doesn't contain regular (non-source) jar, which probably is a bug
      val acpWithoutSource = acp filterNot { _ == "commons-io-1.4.jar"}
      val bcpWithoutSource = bcp filterNot { _ == "commons-io-1.4.jar"}
      val ccpWithoutSource = ccp filterNot { _ == "commons-io-1.4.jar"}
      if (acpWithoutSource == bcpWithoutSource && acpWithoutSource == ccpWithoutSource) ()
      else sys.error("Different classpaths are found:" +
        "\n - a (cached)        " + acpWithoutSource.toString +
        "\n - b (plain)         " + bcpWithoutSource.toString +
        "\n - c (inter-project) " + ccpWithoutSource.toString)
      
      val atestcp = (externalDependencyClasspath in Test in a).value.map {_.data.getName}.sorted filterNot { _ == "commons-io-1.4.jar"}
      val btestcp = (externalDependencyClasspath in Test in b).value.map {_.data.getName}.sorted filterNot { _ == "commons-io-1.4.jar"}
      val ctestcp = (externalDependencyClasspath in Test in c).value.map {_.data.getName}.sorted filterNot { _ == "demo_2.10.jar"} filterNot { _ == "commons-io-1.4.jar"}
      if (ctestcp contains "junit-4.11.jar") {
        sys.error("junit found when it should be excluded: " + ctestcp.toString)
      }

      if (atestcp == btestcp) ()
      else sys.error("Different classpaths are found:" +
        "\n - a test (cached) " + atestcp.toString +
        "\n - b test (plain)  " + btestcp.toString)
    }
  )

