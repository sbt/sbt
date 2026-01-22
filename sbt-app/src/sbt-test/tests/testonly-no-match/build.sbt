ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .aggregate(sub1, sub2)
  .settings(name := "root")

lazy val sub1 = (project in file("sub1"))
  .settings(
    name := "sub1",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
  )

lazy val sub2 = (project in file("sub2"))
  .settings(
    name := "sub2",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
  )
