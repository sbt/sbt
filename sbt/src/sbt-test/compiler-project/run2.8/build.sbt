scalaVersion := "2.8.1"

libraryDependencies += "org.scala-tools.testing" %% "specs" % "1.6.7.2" % "test"

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _)