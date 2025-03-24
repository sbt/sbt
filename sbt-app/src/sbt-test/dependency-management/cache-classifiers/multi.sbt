import xsbti.AppConfiguration

ThisBuild / scalaVersion := "2.12.20"

// TTL of Coursier is 24h
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

val b = project
  .settings(
    localCache,
    libraryDependencies += ("org.example" %% "artifacta" % "1.0.0-SNAPSHOT")
      .withSources().classifier("tests"),
    scalaCompilerBridgeResolvers += userLocalFileResolver(appConfiguration.value),
    externalResolvers := Vector(
      MavenCache("demo", ((ThisBuild / baseDirectory).value / "demo-repo")),
      DefaultMavenRepository
    )
  )

val a = project
  .settings(
    localCache,
    organization := "org.example",
    name := "artifacta",
    version := "1.0.0-SNAPSHOT",
    Test / packageBin / publishArtifact := true,
    publishTo := Some(MavenCache("demo", ((ThisBuild / baseDirectory).value / "demo-repo")))
  )

// use the user local resolver to fetch the SNAPSHOT version of the compiler-bridge
def userLocalFileResolver(appConfig: AppConfiguration): Resolver = {
  val ivyHome = appConfig.provider.scalaProvider.launcher.ivyHome
  Resolver.file("User Local", ivyHome / "local")(Resolver.defaultIvyPatterns)
}
