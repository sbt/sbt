ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))
organization := "org.example"
name := "publish-missing-test"
autoScalaLibrary := false
addArtifact(
  name { n => Artifact(n, "txt", "txt") },
  Def.task {
    val base = baseDirectory.value
    val converter = fileConverter.value
    converter.toVirtualFile((base / "topublish.txt").toPath)
  },
)
