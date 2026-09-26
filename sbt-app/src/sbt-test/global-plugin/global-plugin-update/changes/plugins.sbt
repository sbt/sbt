csrCacheDirectory := (baseDirectory.value / ".." / "coursier-cache").getCanonicalFile

resolvers += {
  val port = java.nio.file.Files.readString((baseDirectory.value / ".." / "repo-port").toPath).trim
  Resolver.url("test-repo", url(s"http://127.0.0.1:$port/"))(using Resolver.ivyStylePatterns)
}

libraryDependencies += "com.example" %% "marker" % "0.1.0-SNAPSHOT"
