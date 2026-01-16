

lazy val localRemote =
  MavenRepository("remote-repo", "file:///tmp/remote-repo")

lazy val common =
  project
    .settings(
      name := "config",
      organization := "com.typesafe",
      version := "0.4.9-SNAPSHOT",
      publishTo := Def.uncached(Some(localRemote)),
      autoScalaLibrary := false,
      crossPaths := false
    )

lazy val analyze =
  project
    .dependsOn(common)
    .settings(
      name := "bad-dependency",
      organization := "com.example",
      version := "1.0.0-SNAPSHOT",
      resolvers += localRemote,
      resolvers += Resolver.mavenLocal,
      resolvers += Resolver.sonatypeRepo("snapshots"),
      fullResolvers := Def.uncached(fullResolvers.value.filterNot(_.name == "inter-project"))
    )



