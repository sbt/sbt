lazy val root = (project in file(".")).
  settings(
    scalaVersion := "2.11.12",
    incOptions := incOptions.value.withIncludeSynthToNameHashing(true)
  )
