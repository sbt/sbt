// `signTask` stands in for sbt-pgp's signing task: it just writes a fake signature file
// and publishes it as an extra artifact with an ".asc" extension.

useIvy := true

organization := "com.example"
name := "foo"
version := "1.0.0"
scalaVersion := "2.12.21"
autoScalaLibrary := false
crossPaths := false
Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false
publishTo := localStaging.value

lazy val signTask = taskKey[HashedVirtualFileRef]("Emulates sbt-pgp's signing task")

signTask := {
  val conv = fileConverter.value
  val out = target.value / "foo-1.0.0.jar.asc"
  IO.write(out, "fake-signature")
  conv.toVirtualFile(out.toPath)
}

addArtifact(Artifact("foo", "asc", "jar.asc"), signTask)
