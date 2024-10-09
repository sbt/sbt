import java.nio.file.Files

scalaVersion := "2.12.8"

resolvers += "authenticated" at sys.env("TEST_REPOSITORY")

coursierExtraCredentials += {
  val content =
    s"""foo.host=${uri(sys.env("TEST_REPOSITORY")).getHost}
       |foo.username=user
       |foo.password=pass
       |foo.auto=true
       |foo.https-only=false
     """.stripMargin
  val dest = baseDirectory.in(ThisBuild).value / "project" / "target" / "cred"
  Files.write(dest.toPath, content.getBytes("UTF-8"))
  lmcoursier.credentials.Credentials(dest)
}

libraryDependencies += "com.abc" % "test" % "0.1"
