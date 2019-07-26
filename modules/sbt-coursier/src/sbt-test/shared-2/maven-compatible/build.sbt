scalaVersion := "2.12.8"

resolvers += Resolver.url(
  "jitpack",
  new URL("https://jitpack.io")
)(
  // patterns should be ignored - and the repo be considered a maven one - because
  // isMavenCompatible is true
  Patterns(
    Resolver.ivyStylePatterns.ivyPatterns,
    Resolver.ivyStylePatterns.artifactPatterns,
    isMavenCompatible = true,
    descriptorOptional = false,
    skipConsistencyCheck = false
  )
)

libraryDependencies += "com.github.jupyter" % "jvm-repr" % "0.3.0"
