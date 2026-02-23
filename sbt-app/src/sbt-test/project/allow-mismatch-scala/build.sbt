lazy val scala3 = project
  .settings(
    scalaVersion := "3.5.1",
  )

lazy val scala213 = project
  .settings(
    scalaVersion := "2.13.16",
  )
  .dependsOn(scala3)
