lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.13.12",
    Compile / doc / scalacOptions += "-no-link-warnings",
    TaskKey[Unit]("checkDocOptions") := {
      val opts = (Compile / doc / scalacOptions).value
      assert(opts.contains("-no-link-warnings"), s"Expected -no-link-warnings in doc scalacOptions but got: $opts")
    }
  )
