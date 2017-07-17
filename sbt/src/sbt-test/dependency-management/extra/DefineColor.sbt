lazy val root = (project in file("."))
  .settings(
    organization := "com.example",
    version := "1.0",
    name := "define-color",
    projectID := {
      val old = projectID.value
      old.extra("e:color" -> "red")
    },
    ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache")),
    publishMavenStyle := false,
    publishTo := {
      val base = baseDirectory.value
      Some(Resolver.file("test-repo", base / "repo" / "test")(Resolver.defaultIvyPatterns))
    }
  )
