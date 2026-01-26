lazy val a = project
  .settings(
    scalaVersion := "3.8.1"
  )

// (b / update) sbt.librarymanagement.ResolveException: Error downloading org.scala-lang:scala-reflect:3.8.1
lazy val b = project
  .dependsOn(a)
  .settings(
    scalaVersion := "2.13.10",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.13.10",
  )
