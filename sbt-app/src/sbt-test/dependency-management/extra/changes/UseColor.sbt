ThisBuild / useCoursier := false
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

lazy val root = (project in file("."))
  .settings(
    localCache,
    organization := "org.example",
    name := "use-color",
    publishMavenStyle := false,
    resolvers := baseDirectory( base =>
      Resolver.file("test-repo", base / "repo" / "test")(Resolver.defaultIvyPatterns) :: Nil
    ).value,
    libraryDependencies := {
      val base = baseDirectory.value
      val color = IO.read(base / "color")
      val dep = "com.example" %% "define-color" % "1.0" extra("e:color" -> color)
      dep :: Nil
    }
  )
