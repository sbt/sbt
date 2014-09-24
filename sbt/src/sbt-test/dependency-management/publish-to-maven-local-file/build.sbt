

lazy val localRemote =
  MavenRepository("remote-repo", "file:///tmp/remote-repo")

lazy val common =
  project
    .settings(
      name := "published-maven",
      organization := "com.example",
      version := "1.0.0-SNAPSHOT",
      publishTo := Some(localRemote)
    )

lazy val analyze =
  project
    .dependsOn(common)
    .settings(
      name := "bad-dependency",
      organization := "com.example",
      version := "1.0.0-SNAPSHOT",
      resolvers += localRemote,
      fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project")
    )



