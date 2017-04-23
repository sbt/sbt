// https://github.com/sbt/sbt/issues/3132
lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys += name,
    buildInfoPackage := "hello"
  )
