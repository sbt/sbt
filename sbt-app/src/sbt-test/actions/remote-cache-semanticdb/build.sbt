name := "my-project"

scalaVersion := "2.12.20"

semanticdbIncludeInJar := true

semanticdbEnabled := true

pushRemoteCacheTo := Some(
  MavenCache("local-cache", (ThisBuild / baseDirectory).value / "remote-cache-semanticdb")
)

Compile / remoteCacheId := "fixed-id"
Compile / remoteCacheIdCandidates := Seq((Compile / remoteCacheId).value)
Test / remoteCacheId := "fixed-id"
Test / remoteCacheIdCandidates := Seq((Test / remoteCacheId).value)
Compile / pushRemoteCacheConfiguration := (Compile / pushRemoteCacheConfiguration).value.withOverwrite(true)
Test / pushRemoteCacheConfiguration := (Test / pushRemoteCacheConfiguration).value.withOverwrite(true)
