// https://github.com/sbt/sbt/issues/1649
lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    scalaVersion := "2.10.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

def cachedResolutionSettings: Seq[Def.Setting[_]] =
  commonSettings ++ Seq(
   updateOptions := updateOptions.value.withCachedResolution(true)
  )

lazy val a = project.
  settings(cachedResolutionSettings: _*).
  settings(
    libraryDependencies += "net.databinder" %% "unfiltered-uploads" % "0.8.0" exclude("commons-io", "commons-io")
  )

lazy val b = project.
  settings(cachedResolutionSettings: _*).
  settings(
    libraryDependencies += "net.databinder" %% "unfiltered-uploads" % "0.8.0"
  )

lazy val root = (project in file(".")).
  aggregate(a, b).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      // sys.error(dependencyCacheDirectory.value.toString)
      val acp = (externalDependencyClasspath in Compile in a).value.sortBy {_.data.getName}
      val bcp = (externalDependencyClasspath in Compile in b).value.sortBy {_.data.getName}
      if (acp exists { _.data.getName contains "commons-io" }) {
        sys.error("commons-io found when it should be excluded")
      }
      if (!(bcp exists { _.data.getName contains "commons-io" })) {
        sys.error("commons-io NOT found when it should NOT be excluded")
      }
    }
  )
