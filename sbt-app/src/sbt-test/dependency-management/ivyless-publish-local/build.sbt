ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

name := "lib1"
organization := "com.example"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.8.2"

// Use a fixed path for local ivy repo to avoid sbt 2.x output sharding
val ivyLocalBase = settingKey[File]("Local Ivy repository base")
ivyLocalBase := baseDirectory.value / "local-ivy-repo"

// Custom ivy paths to write under a fixed directory
ivyPaths := IvyPaths(
  baseDirectory.value.toString,
  Some(ivyLocalBase.value.toString)
)

// Use ivyless publisher
useIvy := false

// Disable doc generation to speed up the test
Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

// Task to print debug info
val printPaths = taskKey[Unit]("Print paths for debugging")
printPaths := {
  val log = streams.value.log
  val ip = ivyPaths.value
  log.info(s"ivyPaths.baseDirectory = ${ip.baseDirectory}")
  log.info(s"ivyPaths.ivyHome = ${ip.ivyHome}")
  log.info(s"ivyLocalBase = ${ivyLocalBase.value}")
  log.info(s"useIvy = ${useIvy.value}")
}

// Task to check that files were published correctly
val checkIvylessPublish = taskKey[Unit]("Check that ivyless publish produced the expected files")
checkIvylessPublish := {
  val log = streams.value.log
  val base = ivyLocalBase.value / "local"
  val org = organization.value
  val moduleName = normalizedName.value + "_3"
  val ver = version.value

  val moduleDir = base / org / moduleName / ver

  log.info(s"ivyLocalBase = ${ivyLocalBase.value}")
  log.info(s"Checking published files in $moduleDir")

  // List what's actually in the ivy-repo directory
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
  log.info("Contents of ivyLocalBase:")
  listDir(ivyLocalBase.value)

  // Check that the main artifacts exist
  val expectedDirs = Seq("jars", "poms", "ivys")
  expectedDirs.foreach { dir =>
    val d = moduleDir / dir
    assert(d.exists && d.isDirectory, s"Expected directory $d to exist")
  }

  // Check jar file and checksums
  val jarFile = moduleDir / "jars" / s"$moduleName.jar"
  assert(jarFile.exists, s"Expected $jarFile to exist")
  assert((moduleDir / "jars" / s"$moduleName.jar.md5").exists, s"Expected md5 checksum to exist")
  assert((moduleDir / "jars" / s"$moduleName.jar.sha1").exists, s"Expected sha1 checksum to exist")

  // Check ivy.xml and checksums
  val ivyFile = moduleDir / "ivys" / "ivy.xml"
  assert(ivyFile.exists, s"Expected $ivyFile to exist")
  assert((moduleDir / "ivys" / "ivy.xml.md5").exists, s"Expected ivy.xml md5 checksum to exist")
  assert((moduleDir / "ivys" / "ivy.xml.sha1").exists, s"Expected ivy.xml sha1 checksum to exist")

  // Check ivy.xml content
  val ivyContent = IO.read(ivyFile)
  assert(ivyContent.contains(s"""organisation="$org""""), s"ivy.xml should contain organisation")
  assert(ivyContent.contains(s"""module="$moduleName""""), s"ivy.xml should contain module name")
  assert(ivyContent.contains(s"""revision="$ver""""), s"ivy.xml should contain revision")

  log.info("All ivyless publish checks passed!")
}

// Task to clean the local ivy repo
val cleanLocalIvy = taskKey[Unit]("Clean the local ivy repo")
cleanLocalIvy := {
  val base = ivyLocalBase.value / "local"
  IO.delete(base)
}
