ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

organization := "org.example"

name := "publish-missing-test"

autoScalaLibrary := false

addArtifact(
	name { n => Artifact(n, "txt", "txt") },
	baseDirectory map { _ / "topublish.txt" }
)
