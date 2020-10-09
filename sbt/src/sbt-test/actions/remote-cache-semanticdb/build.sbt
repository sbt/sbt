name := "my-project"

scalaVersion := "2.12.12"

semanticdbIncludeInJar := true

semanticdbEnabled := true

pushRemoteCacheTo := Some(
  MavenCache("local-cache", (ThisBuild / baseDirectory).value / "remote-cache-semanticdb")
)

remoteCacheId := "fixed-id"

remoteCacheIdCandidates := Seq(remoteCacheId.value)

pushRemoteCacheConfiguration := pushRemoteCacheConfiguration.value.withOverwrite(true)
