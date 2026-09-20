// Deliberately does NOT override ivyPaths or csrCacheDirectory. The point of this test is that
// scripted isolates publishing on its own. See https://github.com/sbt/sbt/issues/1361.

name := "isolated"
organization := "com.example.scripted-isolation"
version := "1.0.0"
scalaVersion := "3.9.0"

Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

val moduleDirName = settingKey[String]("Published module directory name")
moduleDirName := normalizedName.value + "_3"

val checkPublishTargetIsIsolated = taskKey[Unit]("Assert the publish targets are under the test dir")
checkPublishTargetIsIsolated := {
  val log = streams.value.log
  val here = (ThisBuild / baseDirectory).value.getCanonicalFile.toPath.getParent

  val localRepo = localIvyRepository.value.getCanonicalFile
  log.info(s"localIvyRepository = $localRepo")
  assert(
    sys.props.get("sbt.local.repository").isDefined,
    "scripted should have set -Dsbt.local.repository"
  )
  assert(
    localRepo.toPath.startsWith(here),
    s"$localRepo should live under the scripted temporary directory $here"
  )

  val m2 = sys.props.getOrElse("maven.repo.local", sys.error("maven.repo.local was not set"))
  assert(
    file(m2).getCanonicalFile.toPath.startsWith(here),
    s"$m2 should live under the scripted temporary directory $here"
  )
}

val checkCoursierCacheIsShared = taskKey[Unit]("Assert the download cache was not relocated")
checkCoursierCacheIsShared := {
  // Relocating the download cache would make every scripted test re-resolve from the network,
  // which is the objection that kept #1361 open for a decade. Keep it shared.
  val actual = csrCacheDirectory.value
  val expected = lmcoursier.CoursierDependencyResolution.defaultCacheLocation
  assert(actual == expected, s"csrCacheDirectory moved to $actual; it should still be $expected")
}

val checkPublishedHere = taskKey[Unit]("Assert the artifact landed in the isolated repository")
checkPublishedHere := {
  val jar = localIvyRepository.value / organization.value / moduleDirName.value /
    version.value / "jars" / s"${moduleDirName.value}.jar"
  assert(jar.exists, s"Expected $jar to exist")
}

val checkUserRepositoriesUntouched =
  taskKey[Unit]("Assert nothing reached the developer's own repositories")
checkUserRepositoriesUntouched := {
  val ivyLeak = Path.userHome / ".ivy2" / "local" / organization.value
  assert(!ivyLeak.exists, s"publishLocal leaked into $ivyLeak")
  val m2Leak = Path.userHome / ".m2" / "repository" / "com" / "example" / "scripted-isolation"
  assert(!m2Leak.exists, s"publishM2 leaked into $m2Leak")
}

@transient
val checkResolvable = taskKey[Unit]("Assert the isolated repository is on the resolver chain")
checkResolvable := {
  val names = fullResolvers.value.map(_.toString)
  assert(
    names.exists(_.contains("local-publish")),
    s"the isolated publish target should be resolvable, resolvers were $names"
  )
}
