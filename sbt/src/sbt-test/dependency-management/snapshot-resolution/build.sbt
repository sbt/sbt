def customIvyPaths: Seq[Def.Setting[_]] = Seq(
  ivyPaths := IvyPaths((baseDirectory in ThisBuild).value, Some((baseDirectory in ThisBuild).value / "ivy-cache"))
)

lazy val sharedResolver: Resolver = {
  val r = Resolver.defaultShared
  r withConfiguration (r.configuration withIsLocal false)
  //MavenRepository("example-shared-repo", "file:///tmp/shared-maven-repo-bad-example")
  //Resolver.file("example-shared-repo", repoDir)(Resolver.defaultPatterns)
}

lazy val common = project.
  settings(customIvyPaths: _*).
  settings(
    scalaVersion := "2.11.8",
    organization := "com.badexample",
    name := "badexample",
    version := "1.0-SNAPSHOT",
    publishTo := Some(sharedResolver),
    crossVersion := Disabled(),
    publishMavenStyle := (sharedResolver match {
      case repo: PatternsBasedRepository => repo.patterns.isMavenCompatible
      case _: RawRepository => false // TODO - look deeper
      case _: MavenRepository => true
      case _ => false  // TODO - Handle chain repository?
    })
    // updateOptions := updateOptions.value.withLatestSnapshots(true)
  )

lazy val dependent = project.
  settings(customIvyPaths: _*).
  settings(
    scalaVersion := "2.11.8",
    // Uncomment the following to test the before/after
    // updateOptions := updateOptions.value.withLatestSnapshots(false),
    // Ignore the inter-project resolver, so we force to look remotely.
    resolvers += sharedResolver,
    fullResolvers := fullResolvers.value.filterNot(_==projectResolver.value),
    libraryDependencies += "com.badexample" % "badexample" % "1.0-SNAPSHOT"
    // Setting this to false fails the test
    // updateOptions := updateOptions.value.withLatestSnapshots(true)
  )

TaskKey[Unit]("dumpResolvers") := {
  val log = streams.value.log
  log.info(s" -- dependent/fullResolvers -- ")
  (fullResolvers in dependent).value foreach { r =>
    log.info(s" * ${r}")
  }
}
