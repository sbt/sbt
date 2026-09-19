ThisBuild / organization := "com.example"

lazy val root = (project in file("."))

lazy val marker = (project in file("marker"))
  .settings(
    name := "marker",
    version := "0.1.0",
    publishMavenStyle := false,
    publishTo := Some(
      Resolver.file("test-repo", (ThisBuild / baseDirectory).value / "global" / "repo")(using
        Resolver.ivyStylePatterns
      )
    ),
  )
