// https://github.com/sbt/sbt/issues/1649
lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    scalaVersion := "2.10.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

lazy val a = project.
  settings(
    commonSettings,
    libraryDependencies += "net.databinder" %% "unfiltered-uploads" % "0.8.0" exclude("commons-io", "commons-io"),
    ivyXML :=
      <dependencies>
        <exclude module="commons-codec"/>
      </dependencies>
  )

lazy val b = project.
  settings(
    commonSettings,
    libraryDependencies += "net.databinder" %% "unfiltered-uploads" % "0.8.0"
  )

lazy val root = (project in file(".")).
  aggregate(a, b).
  settings(inThisBuild(Seq(
    organization := "org.example",
    version := "1.0",
    updateOptions := updateOptions.value.withCachedResolution(true),
    check := {
      val acp = (externalDependencyClasspath in Compile in a).value.sortBy {_.data.getName}
      val bcp = (externalDependencyClasspath in Compile in b).value.sortBy {_.data.getName}
      if (acp exists { _.data.getName contains "commons-io" }) {
        sys.error("commons-io found when it should be excluded")
      }
      if (acp exists { _.data.getName contains "commons-codec" }) {
        sys.error("commons-codec found when it should be excluded")
      }
      // This is checking to make sure excluded graph is not getting picked up
      if (!(bcp exists { _.data.getName contains "commons-io" })) {
        sys.error("commons-io NOT found when it should NOT be excluded")
      }
    }
  )))
