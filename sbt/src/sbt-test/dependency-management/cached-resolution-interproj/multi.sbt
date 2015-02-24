// https://github.com/sbt/sbt/issues/1730
lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    scalaVersion := "2.11.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

def cachedResolutionSettings: Seq[Def.Setting[_]] =
  commonSettings ++ Seq(
   updateOptions := updateOptions.value.withCachedResolution(true)
  )

lazy val transitiveTest = project.
  settings(cachedResolutionSettings: _*).
  settings(
    libraryDependencies += "junit" % "junit" % "4.11" % Test
  )

lazy val transitiveTestDefault = project.
  settings(cachedResolutionSettings: _*).
  settings(
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1"
  )

lazy val a = project.
dependsOn(transitiveTestDefault % Test, transitiveTest % "test->test").
  settings(cachedResolutionSettings: _*)

lazy val root = (project in file(".")).
  aggregate(a).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      val ur = (update in a).value
      val acp = (externalDependencyClasspath in Compile in a).value.map {_.data.getName}
      val atestcp0 = (fullClasspath in Test in a).value
      val atestcp = (externalDependencyClasspath in Test in a).value.map {_.data.getName}
      // This is checking to make sure interproject dependency works
      if (acp exists { _ contains "scalatest" }) {
        sys.error("scalatest found when it should NOT be included: " + acp.toString)
      }
      // This is checking to make sure interproject dependency works
      if (!(atestcp exists { _ contains "scalatest" })) {
        sys.error("scalatest NOT found when it should be included: " + atestcp.toString)
      }
      // This is checking to make sure interproject dependency works
      if (!(atestcp exists { _ contains "junit" })) {
        sys.error("junit NOT found when it should be included: " + atestcp.toString)
      }
    }
  )
