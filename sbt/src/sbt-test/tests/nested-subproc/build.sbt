val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"
val scalaxml = "org.scala-lang.modules" %% "scala-xml" % "1.1.1"

ThisBuild / scalaVersion := "2.12.11"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= List(scalatest, scalaxml),
    Test / testOptions += Tests.Argument("-C", "custom.CustomReporter"),
    fork := true
  )
