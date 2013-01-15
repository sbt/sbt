organization := "org.example"

name := "artifacta"

version := "1.0.0-SNAPSHOT"

publishArtifact in (Test,packageBin) := true

publishTo := Some(Resolver.file("demo", (baseDirectory in ThisBuild).value / "demo-repo"))
