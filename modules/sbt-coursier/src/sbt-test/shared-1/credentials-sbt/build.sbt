scalaVersion := "2.12.8"

resolvers += "authenticated" at sys.env("TEST_REPOSITORY")

credentials += Credentials(
  "",
  sys.env("TEST_REPOSITORY_HOST"),
  sys.env("TEST_REPOSITORY_USER"),
  sys.env("TEST_REPOSITORY_PASSWORD")
)

libraryDependencies += "com.abc" % "test" % "0.1"
