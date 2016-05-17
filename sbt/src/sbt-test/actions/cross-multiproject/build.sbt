
lazy val rootProj = (project in file(".")).
  aggregate(libProj, fooPlugin)

lazy val libProj = (project in file("lib")).
  settings(
    name := "foo-lib",
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.11.8", "2.10.4")
  )

lazy val fooPlugin =(project in file("sbt-foo")).
  settings(
    name := "sbt-foo",
    sbtPlugin := true,
    scalaVersion := "2.10.4",
    crossScalaVersions := Seq("2.10.4")
  )
