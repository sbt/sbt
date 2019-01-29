lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.10.7",
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.0.M6-SNAP28",
    testOptions in Test += Tests.Argument("-r", "custom.CustomReporter"),
    fork := true,
    javaOptions in Test += "-Dsbt.repository.secure=false"
  )
