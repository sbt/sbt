scalaVersion := "2.11.8"

coursierArtifacts := {
  val f = file("coursier-artifacts")
  if (f.exists())
    sys.error(s"$f file found")

  java.nio.file.Files.write(f.toPath, Array.empty[Byte])
  coursierArtifacts.value
}
