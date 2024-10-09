
lazy val shared = Seq(
  scalaVersion := "2.12.8",
  libraryDependencies ++= Seq(
    "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M4",
    "com.chuusai" %% "shapeless" % "2.3.3"
  ),
  versionReconciliation += "*" % "*" % "strict"
)

lazy val a = project
  .settings(shared)

lazy val b = project
  .settings(shared)
  .settings(
    // strict cm should be fine if we force the conflicting module version
    dependencyOverrides += "com.chuusai" %% "shapeless" % "2.3.3"
  )
