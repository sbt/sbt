ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "local-artifacts-cache-test",
    csrLocalArtifactsShouldBeCached := true,
  )
