organization := "org.example"

name := "artifacta"

version := "1.0.0-SNAPSHOT"

publishArtifact in (Test,packageBin) := true

publishTo <<= (baseDirectory in ThisBuild) { base => Some(Resolver.file("demo", base / "demo-repo")) }
