scalaVersion := "2.12.8"

resolvers += "authenticated" at sys.env("TEST_REPOSITORY")

coursierCredentials += "authenticated" -> coursier.Credentials(file("credentials"))

libraryDependencies += "com.abc" % "test" % "0.1"
