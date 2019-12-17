lazy val root = (project in file("."))
  .aggregate(foo, client)
  .settings(
    crossScalaVersions := Nil
  )

lazy val foo = project
  .settings(
    crossScalaVersions := Seq("2.12.10", "2.13.1"),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.0",
  )

lazy val client = project
  .settings(
    crossScalaVersions := Seq("2.12.10", "2.13.1"),
  )
