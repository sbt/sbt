// A non-snapshot version means publishLocal runs with overwrite = false, which is the case
// that used to skip the copy and leave the previously published artifact in place.
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

name := "overwrite-demo"
organization := "com.example"
version := "1.0.0"
scalaVersion := "3.9.0"

val ivyLocalBase = settingKey[File]("Local Ivy repository base")
ivyLocalBase := baseDirectory.value / "local-ivy-repo"

ivyPaths := IvyPaths(baseDirectory.value.toString, Some(ivyLocalBase.value.toString))

useIvy := false

Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

val publishedJar = settingKey[File]("The jar publishLocal writes into the local Ivy repository")
publishedJar := {
  val moduleName = normalizedName.value + "_3"
  ivyLocalBase.value / "local" / organization.value / moduleName / version.value /
    "jars" / s"$moduleName.jar"
}

val checkMarker = inputKey[Unit]("Assert the published jar carries the given marker")
checkMarker := {
  val expected = Def.spaceDelimited().parsed.mkString(" ").trim
  val jar = publishedJar.value
  assert(jar.exists, s"Expected $jar to exist")
  val actual = {
    val zip = new java.util.zip.ZipFile(jar)
    try {
      val entry = Option(zip.getEntry("marker.txt"))
        .getOrElse(sys.error(s"marker.txt is missing from $jar"))
      scala.util.Using.resource(zip.getInputStream(entry)) { in =>
        new String(in.readAllBytes(), "UTF-8").trim
      }
    } finally zip.close()
  }
  assert(
    actual == expected,
    s"Expected the published jar to carry '$expected', but it carries '$actual'"
  )
}

val checkChecksums = taskKey[Unit]("Assert the published checksums match the published bytes")
checkChecksums := {
  val jar = publishedJar.value
  Seq("md5", "sha1").foreach { algo =>
    val checksumFile = file(jar.getPath + "." + algo)
    assert(checksumFile.exists, s"Expected $checksumFile to exist")
    val expected = sbt.util.Digest(algo, jar.toPath).hashHexString
    val actual = IO.read(checksumFile).trim
    assert(actual == expected, s"$checksumFile records $actual but the jar hashes to $expected")
  }
}

val checkUserIvyUntouched = taskKey[Unit]("Assert nothing was published to the real ~/.ivy2/local")
checkUserIvyUntouched := {
  val leaked = Path.userHome / ".ivy2" / "local" / organization.value / (normalizedName.value + "_3")
  assert(!leaked.exists, s"publishLocal leaked into the user's Ivy repository at $leaked")
}
