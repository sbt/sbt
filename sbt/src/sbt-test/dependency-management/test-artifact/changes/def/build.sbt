organization := "org.example"

name := "def"

version := "2.0"

publishTo := Some(Resolver.file("example", baseDirectory.value / "ivy-repo"))

publishArtifact in Test := true
