lazy val a = project.settings(
  scalaVersion := "2.13.4",
  libraryDependencies += "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4", // depends on library 2.13.6
)
