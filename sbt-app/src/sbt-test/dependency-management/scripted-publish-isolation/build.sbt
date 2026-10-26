name := "isolated"
organization := "com.example.scripted-isolation"
version := "1.0.0"
scalaVersion := "3.9.0"

Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

val checkIsolated = taskKey[Unit]("Assert publishLocal and publishM2 wrote only under the test dir")
checkIsolated := {
  val base = (ThisBuild / baseDirectory).value.getCanonicalFile.toPath
  val module = normalizedName.value + "_3"
  val v = version.value
  val ivyHome = ivyPaths.value.ivyHome.map(file).getOrElse(Path.userHome / ".ivy2")
  def repo(prop: String): File =
    val dir = file(sys.props.getOrElse(prop, sys.error(s"scripted should have set -D$prop")))
    assert(dir.getCanonicalFile.toPath.startsWith(base), s"$dir should live under $base")
    dir

  val ivyJar = repo("sbt.local_repository") / organization.value / module / v / "jars" / s"$module.jar"
  assert(ivyJar.exists, s"Expected $ivyJar to exist")
  val m2Jar = repo("maven.repo.local") / "com" / "example" / "scripted-isolation" / module / v /
    s"$module-$v.jar"
  assert(m2Jar.exists, s"Expected $m2Jar to exist")

  val ivyLeak = ivyHome / "local" / organization.value
  assert(!ivyLeak.exists, s"publishLocal leaked into $ivyLeak")
  val m2Leak = Path.userHome / ".m2" / "repository" / "com" / "example" / "scripted-isolation"
  assert(!m2Leak.exists, s"publishM2 leaked into $m2Leak")
}
