scalaVersion := "2.12.19"

lazy val core = project
  .settings(
    crossScalaVersions := Seq("2.12.19", "3.0.2", "3.3.3")
  )

lazy val subproj = project
  .dependsOn(core)
  .settings(
    crossScalaVersions := Seq("2.12.19", "3.3.3"),
    // a random library compiled against Scala 3.1
    libraryDependencies += "org.http4s" %% "http4s-core" % "0.23.12"
  )
