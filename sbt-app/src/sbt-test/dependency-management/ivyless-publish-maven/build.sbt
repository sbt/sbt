ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

name := "lib1"
organization := "com.example"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.8.4"

publishMavenStyle := true
val publishRepoBase = settingKey[File]("Base directory for Maven publish repo")
publishRepoBase := baseDirectory.value / "repo"

publishTo := Some(sbt.librarymanagement.MavenCache("local-maven", publishRepoBase.value))

useIvy := false

Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

val checkMavenPublish = taskKey[Unit]("Check that ivyless Maven publish produced the expected files")
checkMavenPublish := {
  val log = streams.value.log
  val base = publishRepoBase.value
  val groupId = organization.value
  val artifactId = normalizedName.value + "_3"
  val ver = version.value
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

  log.info("All ivyless Maven publish checks passed!")
}

val cleanPublishRepo = taskKey[Unit]("Clean the publish repo")
cleanPublishRepo := {
  IO.delete(publishRepoBase.value)
}
