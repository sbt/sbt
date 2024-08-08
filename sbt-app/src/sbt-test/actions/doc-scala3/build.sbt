lazy val rc1 = (project in file("rc1"))
  .settings(
    scalaVersion := "3.0.0-RC1"
  )

lazy val a = project
  .settings(
    scalaVersion := "3.4.2",
  )
