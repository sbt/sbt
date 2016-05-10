scalaVersion := "2.11.8"

resolvers += "authenticated" at "http://localhost:8080"

coursierCredentials += "authenticated" -> coursier.Credentials(file("credentials"))

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}

libraryDependencies += "com.abc" % "test" % "0.1"
