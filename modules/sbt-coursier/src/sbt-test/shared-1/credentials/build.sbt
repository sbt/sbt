scalaVersion := "2.11.8"

resolvers += "authenticated" at sys.env("TEST_REPOSITORY")

coursierExtraCredentials += lmcoursier.credentials.Credentials(
  uri(sys.env("TEST_REPOSITORY")).getHost,
  sys.env("TEST_REPOSITORY_USER"),
  sys.env("TEST_REPOSITORY_PASSWORD")
).withHttpsOnly(false).withMatchHost(true)

libraryDependencies += "com.abc" % "test" % "0.1"
