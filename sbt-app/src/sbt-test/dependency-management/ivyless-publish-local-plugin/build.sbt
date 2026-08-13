ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

val ivyLocalBase = settingKey[File]("Local Ivy repository base")

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-test-plugin",
    organization := "com.example",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.12.21",
    pluginCrossBuild / sbtVersion := "1.5.8",
    ivyLocalBase := baseDirectory.value / "local-ivy-repo",
    ivyPaths := IvyPaths(baseDirectory.value.toString, Some(ivyLocalBase.value.toString)),
    useIvy := false,
    Compile / packageDoc / publishArtifact := false,
    Compile / packageSrc / publishArtifact := false,
  )

val cleanLocalIvy = taskKey[Unit]("Clean the local ivy repo")
cleanLocalIvy := {
  IO.delete(ivyLocalBase.value / "local")
}

val checkPublished = taskKey[Unit]("Check ivyless publishLocal wrote the plugin cross-suffix path")
checkPublished := {
  val log = streams.value.log
  val base = ivyLocalBase.value / "local"
  val org = organization.value
  val moduleName = normalizedName.value
  val ver = version.value

  val moduleDir = base / org / moduleName / "scala_2.12" / "sbt_1.0" / ver
  log.info(s"Checking published files in $moduleDir")

  def listDir(dir: File, indent: String = ""): Unit =
    if (dir.exists)
      dir.listFiles.foreach { f =>
        log.info(s"$indent${f.getName}")
        if (f.isDirectory) listDir(f, indent + "  ")
      }
    else log.info(s"${indent}Directory does not exist: $dir")
  log.info("Contents of ivyLocalBase:")
  listDir(ivyLocalBase.value)

  assert(moduleDir.isDirectory, s"Expected plugin cross-suffix directory $moduleDir to exist")

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

  val nonCrossDir = base / org / moduleName / ver
  assert(!nonCrossDir.exists, s"Non-cross directory $nonCrossDir should not exist; cross suffixes are missing")

  log.info("All ivyless publishLocal plugin checks passed!")
}
