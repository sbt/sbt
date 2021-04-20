lazy val root = (project in file(".")).
  aggregate(a,b).
  settings(
    name := "use",
    version := "1.0",
    organization in ThisBuild := "org.example",
    version in ThisBuild := "2.0-SNAPSHOT",
    libraryDependencies += "org.example" % "b" % "2.0-SNAPSHOT",
    ivyPaths := (ivyPaths in ThisBuild).value
  )

lazy val a = project.
  dependsOn(b).
  settings(
    name := "a",
    ivyPaths := (ivyPaths in ThisBuild).value
  )

lazy val b = project.
  settings(
    name := "b",
    ivyPaths := (ivyPaths in ThisBuild).value
  )
