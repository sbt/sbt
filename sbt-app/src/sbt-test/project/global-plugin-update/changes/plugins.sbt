csrCacheDirectory := (baseDirectory.value / ".." / "coursier-cache").getCanonicalFile

resolvers += Resolver.file("test-repo", (baseDirectory.value / ".." / "repo").getCanonicalFile)(using
  Resolver.ivyStylePatterns
)

libraryDependencies += "com.example" %% "marker" % "latest.integration"
