// sbt/sbt#9117: published artifact names must carry the platform suffix, matching the
// coordinate. `platform` is set directly, as sbt-scala-native does.

ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.8.4"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

lazy val mavenRepo = settingKey[File]("shared local Maven repo for the consume round-trip")
ThisBuild / mavenRepo := (ThisBuild / baseDirectory).value / "maven-repo"

def expected(name: String) = s"${name}_native0.5_3"

lazy val ivyless = (project in file("ivyless"))
  .settings(
    name := "libivyless",
    platform := "native0.5",
    crossVersion := CrossVersion.binary,
    useIvy := false,
    publishMavenStyle := true,
    ivyPaths := IvyPaths(baseDirectory.value.toString, Some((target.value / "ivy2").toString)),
    publishTo := Some(MavenCache("platform-publish-local", (ThisBuild / mavenRepo).value)),
    TaskKey[Unit]("check") := {
      val nm = expected("libivyless")
      val dir = target.value / "ivy2" / "local" / organization.value / nm / version.value
      def req(f: File): Unit = assert(f.exists, s"expected $f to exist")
      req(dir / "jars" / s"$nm.jar")
      req(dir / "srcs" / s"$nm-sources.jar")
      val pom = dir / "poms" / s"$nm.pom"
      req(pom)
      assert(
        IO.read(pom).contains(s"<artifactId>$nm</artifactId>"),
        s"POM artifactId must be $nm: ${IO.read(pom)}"
      )
    }
  )

// Must not dependsOn the producer, so the coordinate resolves from the Maven repo rather
// than inter-project - otherwise a suffix-dropped published name would not be caught.
lazy val consumer = (project in file("consumer"))
  .settings(
    publish / skip := true,
    resolvers += MavenCache("platform-publish-local", (ThisBuild / mavenRepo).value),
    libraryDependencies += organization.value % expected("libivyless") % version.value
  )
