lazy val scala3 = project
  .settings(
    scalaVersion := "3.5.1",
  )

lazy val scala213 = project
  .settings(
    scalaVersion := "2.13.16",
  )
  .dependsOn(scala3)

// Separate pair for testing allowMismatchScala override (avoids SIP-51 scala-library conflict)
lazy val dep212 = project
  .settings(
    scalaVersion := "2.12.21",
  )

lazy val app213 = project
  .settings(
    scalaVersion := "2.13.16",
    allowMismatchScala := true,
  )
  .dependsOn(dep212)
