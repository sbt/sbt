lazy val root = (project in file("."))
  .settings(
    organization := "org.scala-sbt",
    version := "1.0-SNAPSHOT",
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
