lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    scripted / excludeFilter := new SimpleFileFilter(_.getName == "skipped")
  )
