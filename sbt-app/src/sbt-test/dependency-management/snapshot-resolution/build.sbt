ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.12"

// TTL is 24h so we can't detect the change
ThisBuild / useCoursier := false
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

lazy val sharedResolver: Resolver = {
  val r = Resolver.defaultShared
  r withConfiguration (r.configuration withIsLocal false)
  //MavenRepository("example-shared-repo", "file:///tmp/shared-maven-repo-bad-example")
  //Resolver.file("example-shared-repo", repoDir)(Resolver.defaultPatterns)
}

lazy val common = project
  .settings(localCache)
  .settings(
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

lazy val dependent = project
  .settings(localCache)
  .settings(
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
  (dependent / fullResolvers).value foreach { r =>
    log.info(s" * ${r}")
  }
}
