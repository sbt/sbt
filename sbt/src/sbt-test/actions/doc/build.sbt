lazy val root = (project in file("."))
  .settings(
    crossPaths := false,
    Compile / doc / scalacOptions += "-Xfatal-warnings"
  )
