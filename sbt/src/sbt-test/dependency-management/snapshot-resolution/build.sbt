def customIvyPaths: Seq[Def.Setting[_]] = Seq(
  ivyPaths := new IvyPaths((baseDirectory in ThisBuild).value, Some((baseDirectory in ThisBuild).value / "ivy-cache"))
)

lazy val sharedResolver =
  Resolver.defaultShared.nonlocal()
  //MavenRepository("example-shared-repo", "file:///tmp/shared-maven-repo-bad-example")
  //Resolver.file("example-shared-repo", repoDir)(Resolver.defaultPatterns)

lazy val common = project.
  settings(customIvyPaths: _*).
  settings(
    organization := "com.badexample",
    name := "badexample",
    version := "1.0-SNAPSHOT",
    publishTo := Some(sharedResolver),
    crossVersion := CrossVersion.Disabled,
    publishMavenStyle := (publishTo.value match {
      case Some(repo) =>
        repo match {
          case repo: PatternsBasedRepository => repo.patterns.isMavenCompatible
          case _: RawRepository => false // TODO - look deeper
          case _: MavenRepository => true
          case _: MavenCache => true
          case _ => false  // TODO - Handle chain repository?
        }
      case _ => true
    })
  )

lazy val dependent = project.
  settings(customIvyPaths: _*).
  settings(
    // Uncomment the following to test the before/after
    // updateOptions := updateOptions.value.withLatestSnapshots(false),
    // Ignore the inter-project resolver, so we force to look remotely.
    resolvers += sharedResolver,
    fullResolvers := fullResolvers.value.filterNot(_==projectResolver.value),
    libraryDependencies += "com.badexample" % "badexample" % "1.0-SNAPSHOT"
  )

TaskKey[Unit]("dumpResolvers") := {
  streams.value.log.info(s" -- dependent/fullResolvers -- ")
  (fullResolvers in dependent).value foreach { r =>
    streams.value.log.info(s" * ${r}")
  }
}