val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"
val scalaxml = "org.scala-lang.modules" %% "scala-xml" % "1.1.1"
ThisBuild / scalaVersion := "2.12.10"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= List(scalaxml, scalatest),
    Test / parallelExecution := false
  )
