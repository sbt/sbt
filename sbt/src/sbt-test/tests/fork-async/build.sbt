testFrameworks += new TestFramework("utest.runner.Framework")

lazy val root = (project in file(".")).
  settings(
    scalaVersion := "2.12.4",
    libraryDependencies += "com.lihaoyi" %% "utest" % "0.6.4" % Test,
    fork in Test := true
  )
