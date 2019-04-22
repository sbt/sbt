ThisBuild / scalaVersion := "2.11.12"

lazy val a = project
  .settings(
    resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"
  )

lazy val b = project
  .dependsOn(a)
  .settings(
    // resolver added in inter-project dependency only - should still be fine
    libraryDependencies += "org.scalaz.stream" %% "scalaz-stream" % "0.7.1a"
  )

lazy val root = project
  .in(file("."))
  .aggregate(a, b)
