lazy val root = (project in file(".")).
  settings(
    name := "use",
    organization := "org.example",
    version := "1.0",
    libraryDependencies += "org.example" % "b" % "2.0-SNAPSHOT",
    ivyPaths := (ivyPaths in ThisBuild).value
  )
