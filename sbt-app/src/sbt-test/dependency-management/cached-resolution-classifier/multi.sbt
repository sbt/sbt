ThisBuild / useCoursier := false

lazy val check = taskKey[Unit]("Runs the check")

ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    localCache,
    dependencyCacheDirectory := (LocalRootProject / baseDirectory).value / "dependency",
    scalaVersion := "2.10.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

lazy val classifierTest = project.
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(
      ("net.sf.json-lib" % "json-lib" % "2.4").classifier("jdk15").intransitive(),
      "commons-io" % "commons-io" % "1.4"
    )
  )

lazy val transitiveTest = project.
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(
      "junit" % "junit" % "4.13.1" % "test"
    )
  )

lazy val a = project.
  dependsOn(classifierTest, transitiveTest % "test->test").
  settings(commonSettings: _*).
  settings(
    updateOptions := updateOptions.value.withCachedResolution(true),
    (Compile / packageBin / artifact) := Artifact("demo"),
    libraryDependencies := Seq(
      "com.typesafe.akka" %% "akka-remote" % "2.3.4" exclude("com.typesafe.akka", "akka-actor_2.10"),
      "net.databinder" %% "unfiltered-uploads" % "0.8.0",
      ("commons-io" % "commons-io" % "1.4").classifier("sources"),
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
      ("commons-io" % "commons-io" % "1.4").classifier("sources"),
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
    (ThisBuild / organization) := "org.example",
    (ThisBuild / version) := "1.0",
    check := {
      val acp = (a / Compile / externalDependencyClasspath).value.map {_.data.name}.sorted
      val bcp = (b / Compile / externalDependencyClasspath).value.map {_.data.name}.sorted
      val ccp = (c / Compile / externalDependencyClasspath).value.map {_.data.name}.sorted filterNot { _ == "demo_2.10.jar"}
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

      val atestcp = (a / Test / externalDependencyClasspath).value.map {_.data.name}.sorted filterNot { _ == "commons-io-1.4.jar"}
      val btestcp = (b / Test / externalDependencyClasspath).value.map {_.data.name}.sorted filterNot { _ == "commons-io-1.4.jar"}
      val ctestcp = (c / Test / externalDependencyClasspath).value.map {_.data.name}.sorted filterNot { _ == "demo_2.10.jar"} filterNot { _ == "commons-io-1.4.jar"}
      if (ctestcp contains "junit-4.13.1.jar") {
        sys.error("junit found when it should be excluded: " + ctestcp.toString)
      }

      if (atestcp == btestcp) ()
      else sys.error("Different classpaths are found:" +
        "\n - a test (cached) " + atestcp.toString +
        "\n - b test (plain)  " + btestcp.toString)
    }
  )

