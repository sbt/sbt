ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.3"

val publishRepoBase = settingKey[File]("Base directory for Maven publish repo (HTTP server writes here)")
ThisBuild / publishRepoBase := (ThisBuild / baseDirectory).value / "repo"
val publishPort = 3031

lazy val root = (project in file("."))
  .aggregate(a, b)
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
    Compile / packageDoc / publishArtifact := false,
    Compile / packageSrc / publishArtifact := false,
  )

lazy val b = project
  .settings(
    libraryDependencies += organization.value %% "a" % version.value,
    resolvers += sbt.librarymanagement.MavenRepo(
      "test-repo",
      s"http://localhost:$publishPort/"
    ).withAllowInsecureProtocol(true),
  )

val startPublishServer = taskKey[Unit]("Start HTTP server that accepts PUT to repo directory")
Global / startPublishServer := {
  HttpPutServer.start(publishPort, (ThisBuild / publishRepoBase).value)
  streams.value.log.info(s"HTTP PUT server started on port $publishPort, writing to ${(ThisBuild / publishRepoBase).value}")
}

val stopPublishServer = taskKey[Unit]("Stop HTTP server")
Global / stopPublishServer := {
  HttpPutServer.stop()
  streams.value.log.info("HTTP PUT server stopped")
}

val checkMavenPublish = taskKey[Unit]("Check that ivyless Maven publish produced the expected files")
Global / checkMavenPublish := {
  val log = streams.value.log
  val base = (ThisBuild / publishRepoBase).value
  val groupId = (ThisBuild / organization).value
  val artifactId = "a_3"
  val ver = (ThisBuild / version).value
  val groupPath = groupId.replace('.', '/')
  val versionDir = base / groupPath / artifactId / ver

  log.info(s"Checking published Maven layout in $versionDir")

  def listDir(dir: File, indent: String = ""): Unit = {
    if (dir.exists) {
      dir.listFiles.foreach { f =>
        log.info(s"$indent${f.getName}")
        if (f.isDirectory) listDir(f, indent + "  ")
      }
    } else {
      log.info(s"${indent}Directory does not exist: $dir")
    }
  }
  log.info("Contents of publish repo:")
  listDir(base)

  assert(versionDir.exists && versionDir.isDirectory, s"Expected version dir $versionDir to exist")

  val pomFile = versionDir / s"$artifactId-$ver.pom"
  assert(pomFile.exists, s"Expected $pomFile to exist")
  assert(new File(pomFile.getPath + ".md5").exists, s"Expected pom md5 checksum")
  assert(new File(pomFile.getPath + ".sha1").exists, s"Expected pom sha1 checksum")

  val jarFile = versionDir / s"$artifactId-$ver.jar"
  assert(jarFile.exists, s"Expected $jarFile to exist")
  assert(new File(jarFile.getPath + ".md5").exists, s"Expected jar md5 checksum")
  assert(new File(jarFile.getPath + ".sha1").exists, s"Expected jar sha1 checksum")

  val pomContent = IO.read(pomFile)
  assert(pomContent.contains(s"<groupId>$groupId</groupId>"), s"POM should contain groupId")
  assert(pomContent.contains(s"<artifactId>$artifactId</artifactId>"), s"POM should contain artifactId")
  assert(pomContent.contains(s"<version>$ver</version>"), s"POM should contain version")

  log.info("All ivyless Maven publish (HTTP) checks passed!")
}

val cleanPublishRepo = taskKey[Unit]("Clean the publish repo")
Global / cleanPublishRepo := {
  IO.delete((ThisBuild / publishRepoBase).value)
}
