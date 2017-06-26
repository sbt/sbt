ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache"))

organization := "org.example"

name := "publish-missing-test"

autoScalaLibrary := false

addArtifact(
	name { n => Artifact(n, "txt", "txt") },
	baseDirectory map { _ / "topublish.txt" }
)
