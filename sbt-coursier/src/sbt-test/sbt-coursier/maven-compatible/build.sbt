scalaVersion := "2.11.8"

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}

resolvers += Resolver.url(
  "webjars-bintray",
  new URL("https://dl.bintray.com/scalaz/releases/")
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

libraryDependencies += "org.scalaz.stream" %% "scalaz-stream" % "0.7.1"
