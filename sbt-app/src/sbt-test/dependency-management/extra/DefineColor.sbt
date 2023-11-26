ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

lazy val root = (project in file("."))
  .settings(
    localCache,
    organization := "com.example",
    version := "1.0",
    name := "define-color",
    projectID := {
      val old = projectID.value
      old.extra("e:color" -> "red")
    },
    publishMavenStyle := false,
    publishTo := {
      val base = baseDirectory.value
      Some(Resolver.file("test-repo", base / "repo" / "test")(Resolver.defaultIvyPatterns))
    }
  )
