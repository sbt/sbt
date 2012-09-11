ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache")))

organization := "org.example"

name := "publish-missing-test"

autoScalaLibrary := false

addArtifact(
	name { n => Artifact(n, "txt", "txt") },
	baseDirectory map { _ / "topublish.txt" }
)