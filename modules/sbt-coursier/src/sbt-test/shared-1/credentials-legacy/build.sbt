scalaVersion := "2.12.8"

resolvers += "authenticated" at sys.env("TEST_REPOSITORY")

coursierCredentials += "authenticated" -> coursier.Credentials(
  sys.env("TEST_REPOSITORY_USER"),
  sys.env("TEST_REPOSITORY_PASSWORD")
)

libraryDependencies += "com.abc" % "test" % "0.1"
