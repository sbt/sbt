lazy val sharedResolver =
  Resolver.defaultShared.nonlocal()
  //MavenRepository("example-shared-repo", "file:///tmp/shared-maven-repo-bad-example")
  //Resolver.file("example-shared-repo", repoDir)(Resolver.defaultPatterns)

lazy val common = (
  project
  .settings(
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
          case _ => false  // TODO - Handle chain repository?
        }
      case _ => true
    })
  )
)

lazy val dependent = (
  project
  .settings(
    // Ignore the inter-project resolver, so we force to look remotely.
    resolvers += sharedResolver,
    fullResolvers := fullResolvers.value.filterNot(_==projectResolver.value),
    libraryDependencies += "com.badexample" % "badexample" % "1.0-SNAPSHOT"
  )
)

TaskKey[Unit]("cleanLocalCache") := {
 val ivyHome = file(sys.props.get("ivy.home")  orElse sys.props.get("sbt.ivy.home") match {
   case Some(home) => home
   case None => s"${sys.props("user.home")}/.ivy2"
 })
 val ivyCache = ivyHome / "cache"
 val ivyShared = ivyHome / "shared"
 val ivyLocal = ivyHome / "local"
 def deleteDirContents(dir: String)(base: File): Unit = {
   val toDelete = base / dir
   streams.value.log.info(s"Deleting: ${toDelete.getAbsolutePath}")
   IO.delete(toDelete)
 }
 Seq(ivyCache, ivyShared, ivyLocal).map(deleteDirContents("com.badexample"))
}

TaskKey[Unit]("dumpResolvers") := {
  streams.value.log.info(s" -- dependent/fullResolvers -- ")
  (fullResolvers in dependent).value foreach { r =>
    streams.value.log.info(s" * ${r}")
  }
}