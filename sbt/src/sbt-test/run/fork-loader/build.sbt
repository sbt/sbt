lazy val root = (project in file("."))
  .settings(
    scalaVersion in ThisBuild := "2.11.8",
    name := "forked-test",
    organization := "org.example",
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.10.0" % Test
  )
