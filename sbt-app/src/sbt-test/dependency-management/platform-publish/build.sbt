// sbt/sbt#9117: published artifact filenames must carry the platform suffix
// (e.g. _native0.5), matching the module coordinate / directory.

ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.3"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

ThisBuild / platform := "native0.5"
ThisBuild / crossVersion := CrossVersion.binary
// expected cross+platform base name, identical to the coordinate directory
def expected(name: String) = s"${name}_native0.5_3"

// ivyless backend: the published filenames come from the coursier publication names,
// and the POM artifactId from PomGenerator.
lazy val ivyless = (project in file("ivyless"))
  .settings(
    useIvy := false,
    ivyPaths := IvyPaths(baseDirectory.value.toString, Some((target.value / "ivy2").toString)),
    TaskKey[Unit]("check") := {
      val nm = expected(moduleName.value)
      val dir = target.value / "ivy2" / "local" / organization.value / nm / version.value
      def req(f: File): Unit = assert(f.exists, s"expected $f to exist")
      req(dir / "jars" / s"$nm.jar")
      req(dir / "srcs" / s"$nm-sources.jar")
      val pom = dir / "poms" / s"$nm.pom"
      req(pom)
      assert(IO.read(pom).contains(s"<artifactId>$nm</artifactId>"), s"POM artifactId must be $nm: ${IO.read(pom)}")
    }
  )

// Ivy backend (sbt-ivy): the published filenames come from CrossVersion.substituteCross.
lazy val ivyfull = (project in file("ivyfull"))
  .settings(
    useIvy := true,
    publishMavenStyle := true,
    ivyPaths := IvyPaths(baseDirectory.value.toString, Some((target.value / "ivy2").toString)),
    publishTo := Some(MavenCache("test-maven", target.value / "maven-repo")),
    TaskKey[Unit]("check") := {
      val nm = expected(moduleName.value)
      val ver = version.value
      def req(f: File): Unit = assert(f.exists, s"expected $f to exist")
      val ivyDir = target.value / "ivy2" / "local" / organization.value / nm / ver
      req(ivyDir / "jars" / s"$nm.jar")
      req(ivyDir / "srcs" / s"$nm-sources.jar")
      req(ivyDir / "poms" / s"$nm.pom")
      val mvnDir = target.value / "maven-repo" / organization.value.replace('.', '/') / nm / ver
      req(mvnDir / s"$nm-$ver.jar")
      req(mvnDir / s"$nm-$ver.pom")
    }
  )
