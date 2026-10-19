ThisBuild / scalaVersion := "2.13.16"

val munit = "org.scalameta" %% "munit" % "1.0.4"

lazy val root = (project in file("."))
  .aggregate(sub1, sub2)
  .settings(name := "root")

lazy val sub1 = (project in file("sub1"))
  .settings(
    name := "sub1",
    libraryDependencies += munit % Test
  )

lazy val sub2 = (project in file("sub2"))
  .settings(
    name := "sub2",
    libraryDependencies += munit % Test
  )
