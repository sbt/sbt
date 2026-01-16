lazy val check = taskKey[Unit]("check")

lazy val root = (project in file("."))
  .settings(
    check := {
      val version = sys.props("java.version").stripPrefix("1.").takeWhile(_.isDigit)
      val expected = sys.props("scripted.java.version")
      assert(
        version == expected,
        s"Expected Java version is '$expected', but actual is '$version'"
      )
    }
  )
