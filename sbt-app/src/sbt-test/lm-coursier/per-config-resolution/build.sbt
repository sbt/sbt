scalaVersion := "2.13.2"
libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier-core" % "2.0.0-RC6",
  // depends on coursier-core 2.0.0-RC6-16
  "io.get-coursier" %% "coursier" % "2.0.0-RC6-16" % Test
)
mainClass.in(Compile) := Some("Main")
mainClass.in(Test) := Some("Test")
