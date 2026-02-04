ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

name := "lib1"
organization := "com.example"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.8.1"

// Publish to a file repo (tests ivyless publish without HTTP server)
val publishRepoBase = settingKey[File]("Base directory for publish repo")
publishRepoBase := baseDirectory.value / "repo"

publishTo := Some(Resolver.file("test-repo", publishRepoBase.value)(Resolver.ivyStylePatterns))

useIvy := false

Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

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

  log.info("All ivyless publish checks passed!")
}

val cleanPublishRepo = taskKey[Unit]("Clean the publish repo")
cleanPublishRepo := {
  IO.delete(publishRepoBase.value)
}
