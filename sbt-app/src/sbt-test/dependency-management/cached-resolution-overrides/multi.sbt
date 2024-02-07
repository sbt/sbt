lazy val check = taskKey[Unit]("Runs the check")

ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ThisBuild / organization := "org.example"
ThisBuild / version := "1.0"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    localCache,
    dependencyCacheDirectory := (LocalRootProject / baseDirectory).value / "dependency",
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
    check := {
      val acp = (a / Compile / externalDependencyClasspath).value.sortBy {_.data.name}
      val bcp = (b / Compile / externalDependencyClasspath).value.sortBy {_.data.name}
      if (acp == bcp) ()
      else sys.error("Different classpaths are found:" +
        "\n - a (overrides + cached) " + acp.toString +
        "\n - b (overrides + plain)  " + bcp.toString) 
    }
  )
