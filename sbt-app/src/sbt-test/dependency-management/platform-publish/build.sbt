// sbt/sbt#9117: published artifact names must carry the platform suffix, matching the
// coordinate, on both publish backends. `platform` is set directly, as sbt-scala-native does.

ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.8.3"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

lazy val mavenRepo = settingKey[File]("shared local Maven repo for the consume round-trip")
ThisBuild / mavenRepo := (ThisBuild / baseDirectory).value / "maven-repo"

def expected(name: String) = s"${name}_native0.5_3"

def producer(useIvyFlag: Boolean): Seq[Setting[?]] = Seq(
  platform := "native0.5",
  crossVersion := CrossVersion.binary,
  useIvy := useIvyFlag,
  publishMavenStyle := true,
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some((target.value / "ivy2").toString)),
  publishTo := Some(MavenCache("platform-publish-local", (ThisBuild / mavenRepo).value))
)

// checkPomArtifactId only for ivyless: there the artifactId comes from PomGenerator (and
// resolution does not validate POM content); the Ivy backend's is correct regardless.
def ivyLayoutCheck(base: String, checkPomArtifactId: Boolean): Setting[?] =
  TaskKey[Unit]("check") := {
    val nm = expected(base)
    val dir = target.value / "ivy2" / "local" / organization.value / nm / version.value
    def req(f: File): Unit = assert(f.exists, s"expected $f to exist")
    req(dir / "jars" / s"$nm.jar")
    req(dir / "srcs" / s"$nm-sources.jar")
    val pom = dir / "poms" / s"$nm.pom"
    req(pom)
    if (checkPomArtifactId)
      assert(IO.read(pom).contains(s"<artifactId>$nm</artifactId>"), s"POM artifactId must be $nm: ${IO.read(pom)}")
  }

lazy val ivyless = (project in file("ivyless"))
  .settings(name := "libivyless", producer(false), ivyLayoutCheck("libivyless", checkPomArtifactId = true))

lazy val ivyfull = (project in file("ivyfull"))
  .settings(name := "libivyfull", producer(true), ivyLayoutCheck("libivyfull", checkPomArtifactId = false))

// Must not dependsOn the producers, so the coordinates resolve from the Maven repo rather
// than inter-project - otherwise a suffix-dropped published name would not be caught.
lazy val consumer = (project in file("consumer"))
  .settings(
    publish / skip := true,
    resolvers += MavenCache("platform-publish-local", (ThisBuild / mavenRepo).value),
    libraryDependencies += organization.value % expected("libivyless") % version.value,
    libraryDependencies += organization.value % expected("libivyfull") % version.value
  )
