import java.nio.file.Files

scalaVersion := "2.12.8"

resolvers += "authenticated" at sys.env("TEST_REPOSITORY")

csrExtraCredentials += {
  val content =
    s"""foo.host=${uri(sys.env("TEST_REPOSITORY")).getHost}
       |foo.username=user
       |foo.password=pass
       |foo.auto=true
       |foo.https-only=false
     """.stripMargin
  val dest = (ThisBuild / baseDirectory).value / "project" / "target" / "cred"
  Files.write(dest.toPath, content.getBytes("UTF-8"))
  lmcoursier.credentials.FileCredentials(dest.toString)
}

libraryDependencies += "com.abc" % "test" % "0.1"
