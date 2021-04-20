// https://github.com/sbt/sbt/issues/3132
lazy val root = (project in file(".")).
  enablePlugins(XBuildInfoPlugin).
  settings(
    buildInfoKeys += name
  )
