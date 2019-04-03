import java.nio.file.Files

scalaVersion := "2.11.8"

resolvers += "authenticated" at sys.env("TEST_REPOSITORY")

coursierExtraCredentials += {
  val content =
    s"""foo.host=${uri(sys.env("TEST_REPOSITORY")).getHost}
       |foo.username=user
       |foo.password=pass
     """.stripMargin
  val dest = baseDirectory.in(ThisBuild).value / "project" / "target" / "cred"
  Files.write(dest.toPath, content.getBytes("UTF-8"))
  coursier.credentials.Credentials(dest)
}

libraryDependencies += "com.abc" % "test" % "0.1"
