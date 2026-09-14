resolvers += Resolver.file("test-repo", (baseDirectory.value / ".." / "repo").getCanonicalFile)(using
  Resolver.ivyStylePatterns
)
