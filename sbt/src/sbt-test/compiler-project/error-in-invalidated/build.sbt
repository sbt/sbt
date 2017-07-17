lazy val root = (project in file(".")).
  settings(
    incOptions := xsbti.compile.IncOptions.of(),
    scalaVersion := "2.11.7"
  )
