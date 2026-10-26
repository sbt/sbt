// A non-snapshot version means publish runs with overwrite = false. A remote repository may be
// immutable, so publishing over an artifact that is already there must fail rather than clobber it.
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

name := "lib1"
organization := "com.example"
version := "1.0.0"
scalaVersion := "3.9.0"

val publishRepoBase = settingKey[File]("Base directory the HTTP server writes to")
publishRepoBase := baseDirectory.value / "repo"

// 3030-3033 are taken by the other HTTP publish fixtures, which share this JVM in batch mode
val publishPort = 3034

publishTo := Some(
  Resolver.uri("test-repo", uri(s"http://localhost:$publishPort/"))(using Resolver.ivyStylePatterns)
    .withAllowInsecureProtocol(true)
)

useIvy := false

Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

val startPublishServer = taskKey[Unit]("Start the HTTP repository")
startPublishServer := {
  val base = publishRepoBase.value
  IO.createDirectory(base)
  HttpRepoServer.start(publishPort, base)
  streams.value.log.info(s"HTTP repository started on port $publishPort, writing to $base")
}

val stopPublishServer = taskKey[Unit]("Stop the HTTP repository")
stopPublishServer := {
  HttpRepoServer.stop()
  streams.value.log.info("HTTP repository stopped")
}

val checkPublished = taskKey[Unit]("Assert the jar reached the HTTP repository")
checkPublished := {
  val moduleName = normalizedName.value + "_3"
  val jar = publishRepoBase.value / organization.value / moduleName / version.value /
    "jars" / s"$moduleName.jar"
  assert(jar.exists, s"Expected $jar to have been published")
}
