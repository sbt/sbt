
lazy val semver61 = project
  .settings(
    scalaVersion := "2.11.12",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M11",
      "io.argonaut" %% "argonaut" % "6.1"
    ),
    versionReconciliation += "*" % "*" % "semver"
  )

lazy val semver62 = project
  .settings(
    scalaVersion := "2.11.12",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M11",
      "io.argonaut" %% "argonaut" % "6.2"
    ),
    versionReconciliation += "*" % "*" % "semver"
  )

lazy val strict62 = project
  .settings(
    scalaVersion := "2.11.12",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M11",
      "io.argonaut" %% "argonaut" % "6.2"
    ),
    versionReconciliation += "*" % "*" % "strict"
  )

