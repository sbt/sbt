lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    libraryDependencies := Seq(
      "org.springframework" % "spring-core" % "3.2.2.RELEASE" force() exclude("org.springframework", "spring-asm"),
      "org.springframework" % "spring-context" % "4.0.3.RELEASE" exclude("org.springframework", "spring-asm")
    ),
    scalaVersion := "2.10.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

def cachedResolutionSettings: Seq[Def.Setting[_]] =
  commonSettings ++ Seq(
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

lazy val a = project.
  settings(cachedResolutionSettings: _*)

lazy val b = project.
  settings(commonSettings: _*)

lazy val c = project.
  dependsOn(a).
  settings(cachedResolutionSettings: _*)

lazy val root = (project in file(".")).
  aggregate(a, b, c).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      // sys.error(dependencyCacheDirectory.value.toString)
      val acp = (externalDependencyClasspath in Compile in a).value.sortBy {_.data.getName}
      val bcp = (externalDependencyClasspath in Compile in b).value.sortBy {_.data.getName}
      val ccp = (externalDependencyClasspath in Compile in c).value.sortBy {_.data.getName}
      if (acp == bcp && acp == ccp) ()
      else sys.error("Different classpaths are found:" +
        "\n - a (consolidated)  " + acp.toString +
        "\n - b (plain)         " + bcp.toString +
        "\n - c (inter-project) " + ccp.toString) 
    }
  )
