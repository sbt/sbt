scalaVersion := "2.12.8"

resolvers += Resolver.uri(
  "jitpack",
  uri("https://jitpack.io")
)(
  // patterns should be ignored - and the repo be considered a maven one - because
  // isMavenCompatible is true
  using Patterns(
    Resolver.ivyStylePatterns.ivyPatterns,
    Resolver.ivyStylePatterns.artifactPatterns,
    isMavenCompatible = true,
    descriptorOptional = false,
    skipConsistencyCheck = false
  )
)

libraryDependencies += "com.github.jupyter" % "jvm-repr" % "0.3.0"
