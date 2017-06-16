scalaVersion := "2.11.8"

resolvers += "authenticated" at "http://localhost:8080"

coursierUseSbtCredentials := true
credentials += Credentials("", "localhost", "user", "pass")

libraryDependencies += "com.abc" % "test" % "0.1"
