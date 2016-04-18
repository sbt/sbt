lazy val root = (project in file(".")).
  settings(
    incOptions := xsbti.compile.IncOptionsUtil.defaultIncOptions,
    scalaVersion := "2.11.7"
  )
