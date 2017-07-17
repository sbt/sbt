lazy val root = (project in file("."))
  .settings(
    organization := "org.example",
    name := "use-color",
    ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache")),
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
