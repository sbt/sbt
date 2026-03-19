ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

name := "lib1"
organization := "com.example"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.8.2"

val publishRepoBase = settingKey[File]("Base directory for publish repo (HTTP server writes here)")
publishRepoBase := baseDirectory.value / "repo"

val publishPort = 3030

// Publish to HTTP server (localhost) - ivyless publish uses PUT
// Resolver.url expects java.net.URL; in build.sbt "url" is sbt.URI, so use java.net.URL explicitly
publishTo := Some(
  Resolver.url("test-repo", new java.net.URI(s"http://localhost:$publishPort/").toURL)(Resolver.ivyStylePatterns)
    .withAllowInsecureProtocol(true)
)

useIvy := false

Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

val startPublishServer = taskKey[Unit]("Start HTTP server that accepts PUT to repo directory")
startPublishServer := {
  HttpPutServer.start(publishPort, publishRepoBase.value)
  streams.value.log.info(s"HTTP PUT server started on port $publishPort, writing to ${publishRepoBase.value}")
}

val stopPublishServer = taskKey[Unit]("Stop HTTP server")
stopPublishServer := {
  HttpPutServer.stop()
  streams.value.log.info("HTTP PUT server stopped")
}

val publishToHttp = taskKey[Unit]("Publish to HTTP server (start server, publish, stop server)")
publishToHttp := {
  startPublishServer.value
  try publish.value
  finally stopPublishServer.value
}

val checkIvylessPublish = taskKey[Unit]("Check that ivyless publish produced the expected files")
checkIvylessPublish := {
  val log = streams.value.log
  val base = publishRepoBase.value
  val org = organization.value
  val moduleName = normalizedName.value + "_3"
  val ver = version.value
  val moduleDir = base / org / moduleName / ver

  log.info(s"Checking published files in $moduleDir")

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

  val expectedDirs = Seq("jars", "poms", "ivys")
  expectedDirs.foreach { dir =>
    val d = moduleDir / dir
    assert(d.exists && d.isDirectory, s"Expected directory $d to exist")
  }

  val jarFile = moduleDir / "jars" / s"$moduleName.jar"
  assert(jarFile.exists, s"Expected $jarFile to exist")
  assert((moduleDir / "jars" / s"$moduleName.jar.md5").exists, s"Expected md5 checksum to exist")
  assert((moduleDir / "jars" / s"$moduleName.jar.sha1").exists, s"Expected sha1 checksum to exist")

  val ivyFile = moduleDir / "ivys" / "ivy.xml"
  assert(ivyFile.exists, s"Expected $ivyFile to exist")
  assert((moduleDir / "ivys" / "ivy.xml.md5").exists, s"Expected ivy.xml md5 checksum to exist")
  assert((moduleDir / "ivys" / "ivy.xml.sha1").exists, s"Expected ivy.xml sha1 checksum to exist")

  val ivyContent = IO.read(ivyFile)
  assert(ivyContent.contains(s"""organisation="$org""""), s"ivy.xml should contain organisation")
  assert(ivyContent.contains(s"""module="$moduleName""""), s"ivy.xml should contain module name")
  assert(ivyContent.contains(s"""revision="$ver""""), s"ivy.xml should contain revision")

  log.info("All ivyless publish (HTTP) checks passed!")
}

val cleanPublishRepo = taskKey[Unit]("Clean the publish repo")
cleanPublishRepo := {
  IO.delete(publishRepoBase.value)
}
