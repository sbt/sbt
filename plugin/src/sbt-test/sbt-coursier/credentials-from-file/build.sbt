scalaVersion := "2.11.8"

resolvers += "authenticated" at "http://localhost:8080"

coursierCredentials += "authenticated" -> coursier.Credentials(file("credentials"))

coursierCachePolicies := Seq(coursier.CachePolicy.ForceDownload)

libraryDependencies += "com.abc" % "test" % "0.1"
