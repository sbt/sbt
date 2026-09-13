ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.4"

val publishRepoBase = settingKey[File]("Base directory the fake Maven repo writes to")
ThisBuild / publishRepoBase := (ThisBuild / baseDirectory).value / "repo"
val publishPort = 3033

lazy val root = (project in file("."))
  .aggregate(a)
  .settings(
    publish / skip := true,
  )

lazy val a = project
  .settings(
    publishMavenStyle := true,
    publishTo := Some(
      sbt.librarymanagement.MavenRepo("test-repo", s"http://localhost:$publishPort/")
        .withAllowInsecureProtocol(true)
    ),
    useIvy := false,
    Compile / packageDoc / publishArtifact := true,
    Compile / packageSrc / publishArtifact := true,
  )

val startPublishServer = taskKey[Unit]("Start the fake unique-snapshot Maven repo")
Global / startPublishServer := {
  UniqueSnapshotRepoServer.start(publishPort, (ThisBuild / publishRepoBase).value)
  streams.value.log.info(s"unique-snapshot repo listening on $publishPort")
}

val stopPublishServer = taskKey[Unit]("Stop the fake unique-snapshot Maven repo")
Global / stopPublishServer := {
  UniqueSnapshotRepoServer.stop()
}

val cleanPublishRepo = taskKey[Unit]("Clean the publish repo")
Global / cleanPublishRepo := {
  IO.delete((ThisBuild / publishRepoBase).value)
}

val checkSnapshotConsistency = taskKey[Unit]("All files of one snapshot publication share one timestamp/buildNumber")
Global / checkSnapshotConsistency := {
  val log = streams.value.log
  val artifactId = "a_3"
  val baseVersion = "0.1.0"
  val versionDir =
    (ThisBuild / publishRepoBase).value /
      (ThisBuild / organization).value.replace('.', '/') / artifactId / s"$baseVersion-SNAPSHOT"

  assert(versionDir.isDirectory, s"Expected $versionDir to exist")

  val Qualifier = """.*-(\d{8}\.\d{6}-\d+).*""".r
  val artifacts = versionDir.listFiles
    .map(_.getName)
    .filterNot(n => n.startsWith("maven-metadata"))
    .filterNot(n => Seq(".md5", ".sha1", ".sha256", ".sha512", ".asc").exists(n.endsWith))
    .toSeq
    .sorted
  log.info(s"published files: ${artifacts.mkString(", ")}")

  val qualifiers = artifacts.map {
    case n @ Qualifier(q) => q
    case n                => sys.error(s"$n was not given a unique snapshot qualifier")
  }.distinct
  assert(
    qualifiers.size == 1,
    s"one publication produced ${qualifiers.size} snapshot qualifiers: ${qualifiers.mkString(", ")}"
  )
  val qualifier = qualifiers.head

  val metadata = scala.xml.XML.loadFile(versionDir / "maven-metadata.xml")
  val snapshot = metadata \ "versioning" \ "snapshot"
  val summary = s"${(snapshot \ "timestamp").text}-${(snapshot \ "buildNumber").text}"
  assert(
    summary == qualifier,
    s"maven-metadata.xml summary is $summary but the artifacts are $qualifier"
  )

  // the file name Gradle builds from the summary block
  val mainJar = versionDir / s"$artifactId-$baseVersion-$summary.jar"
  assert(mainJar.isFile, s"${mainJar.getName} referenced by maven-metadata.xml does not exist")

  (metadata \\ "snapshotVersion").foreach { sv =>
    val value = (sv \ "value").text
    assert(value.endsWith(qualifier), s"snapshotVersion $value does not match $qualifier")
  }
  log.info("snapshot publication is consistent")
}
