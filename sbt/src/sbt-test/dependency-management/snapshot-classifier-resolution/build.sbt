def customIvyPaths: Seq[Def.Setting[_]] = Seq(
  ivyPaths := new IvyPaths((baseDirectory in ThisBuild).value, Some((baseDirectory in ThisBuild).value / "ivy-cache"))
)

lazy val packageTests = taskKey[File]("package tests")

lazy val common = project.
  settings(customIvyPaths: _*).
  settings(
    organization := "com.badexample",
    name := "badexample",
    version := "1.0-SNAPSHOT",
    publishTo := {
      val p = ivyPaths.value.ivyHome.get
      Some(Resolver.file("maven-share-publish", p / "shared").mavenStyle())
    },
    crossVersion := CrossVersion.Disabled,
    publishMavenStyle := true,
    packageTests in Compile := (packageBin in Test).value,
    artifact in (Compile, packageTests) := Artifact(name.value, "tests"),
    addArtifact(artifact in (Compile, packageTests), packageTests in Compile)
  )

lazy val dependent = project.
  settings(customIvyPaths: _*).
  settings(
    // sharedResolver didn't work.
    resolvers += {
      val p = ivyPaths.value.ivyHome.get
      new MavenRepository("maven-share", p.toURL.toString + "/shared")
    },
    // Ignore the inter-project resolver, so we force to look remotely.
    fullResolvers := fullResolvers.value.filterNot(_==projectResolver.value),
    libraryDependencies += "com.badexample" % "badexample" % "1.0-SNAPSHOT" classifier("tests"),
    libraryDependencies += "com.badexample" % "badexample" % "1.0-SNAPSHOT"
    // updateOptions := updateOptions.value.withLatestSnapshots(true)
  )

TaskKey[Unit]("dumpResolvers") := {
  streams.value.log.info(s" -- dependent/fullResolvers -- ")
  (fullResolvers in dependent).value foreach { r =>
    streams.value.log.info(s" * ${r}")
  }
}
