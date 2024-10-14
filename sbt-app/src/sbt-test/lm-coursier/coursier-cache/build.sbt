TaskKey[Unit]("checkIsDefaultCache") := {
  val csrCacheDir = csrCacheDirectory.value
  assert(csrCacheDir == sbt.coursierint.LMCoursier.defaultCacheLocation, csrCacheDir.toString)
  val expectedPath = if Util.isWindows then "Coursier\\Cache\\v1" else ".cache/coursier/v1"
  assert(csrCacheDir.toString.endsWith(expectedPath), csrCacheDir.toString)
}

TaskKey[Unit]("checkIsCustomCache") := {
  val csrCacheDir = csrCacheDirectory.value
  val ip = ivyPaths.value
  assert(csrCacheDir == file(ip.ivyHome.get) / "coursier-cache", csrCacheDir.toString)
}
