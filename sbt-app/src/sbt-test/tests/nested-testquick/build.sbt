Global / cacheStores := Seq.empty

ThisBuild / scalaVersion := "2.12.21"

lazy val root = (project in file("."))
  .settings(
    crossPaths := false,
    autoScalaLibrary := false,
    libraryDependencies += "com.github.sbt" % "junit-interface" % "0.13.2" % Test,
    Test / parallelExecution := false,
  )
