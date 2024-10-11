scalaVersion := "2.12.20"

lazy val core = project
  .settings(
    crossScalaVersions := Seq("2.12.20", "3.0.2", "3.1.2")
  )

lazy val subproj = project
  .dependsOn(core)
  .settings(
    crossScalaVersions := Seq("2.12.20", "3.1.2"),
    // a random library compiled against Scala 3.1
    libraryDependencies += "org.http4s" %% "http4s-core" % "0.23.12"
  )
