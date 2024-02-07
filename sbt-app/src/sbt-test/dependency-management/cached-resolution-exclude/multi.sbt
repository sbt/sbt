// https://github.com/sbt/sbt/issues/1649
lazy val check = taskKey[Unit]("Runs the check")

ThisBuild / useCoursier := false
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
      val acp = (a / Compile / externalDependencyClasspath).value.sortBy {_.data.name}
      val bcp = (b / Compile / externalDependencyClasspath).value.sortBy {_.data.name}
      if (acp exists { _.data.name contains "commons-io" }) {
        sys.error("commons-io found when it should be excluded")
      }
      if (acp exists { _.data.name contains "commons-codec" }) {
        sys.error("commons-codec found when it should be excluded")
      }
      // This is checking to make sure excluded graph is not getting picked up
      if (!(bcp exists { _.data.name contains "commons-io" })) {
        sys.error("commons-io NOT found when it should NOT be excluded")
      }
    }
  )))
