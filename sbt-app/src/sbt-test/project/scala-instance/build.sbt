scalaVersion := "2.13.16"
autoScalaLibrary := false
managedScalaInstance := false
ivyConfigurations ++= List(Configurations.ScalaTool, Configurations.ZincTool)
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % "2.13.16",
  "org.scala-lang" % "scala-compiler" % "2.13.16" % "scala-tool",
  "org.scala-lang" % "scala2-sbt-bridge" % "2.13.16" % "zinc-tool",
)
