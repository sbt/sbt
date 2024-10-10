import xsbti.AppConfiguration

ThisBuild / scalaVersion := "2.12.20"

// TTL of Coursier is 24h
ThisBuild / useCoursier := false
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
	ivyPaths := IvyPaths(baseDirectory.value, Some((baseDirectory in ThisBuild).value / "ivy" / "cache"))

val b = project
  .settings(
    localCache,
    libraryDependencies += "org.example" %% "artifacta" % "1.0.0-SNAPSHOT" withSources() classifier("tests"),
    scalaCompilerBridgeResolvers += userLocalFileResolver(appConfiguration.value),
    externalResolvers := Vector(
      MavenCache("demo", ((baseDirectory in ThisBuild).value / "demo-repo")),
      DefaultMavenRepository
    )
  )

val a = project
  .settings(
    localCache,
    organization := "org.example",
    name := "artifacta",
    version := "1.0.0-SNAPSHOT",
    publishArtifact in (Test,packageBin) := true,
    publishTo := Some(MavenCache("demo", ((baseDirectory in ThisBuild).value / "demo-repo")))
  )

// use the user local resolver to fetch the SNAPSHOT version of the compiler-bridge
def userLocalFileResolver(appConfig: AppConfiguration): Resolver = {
  val ivyHome = appConfig.provider.scalaProvider.launcher.ivyHome
  Resolver.file("User Local", ivyHome / "local")(Resolver.defaultIvyPatterns)
}
