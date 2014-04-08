import sbt._
import Keys._

object build extends Build {
	val defaultSettings = Seq(
		libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ ),
		incOptions := incOptions.value.withNameHashing(true),
		scalaVersion := "2.11.0-RC3"
	)

	lazy val root = Project(
	   base = file("."),
	   id = "macro",
	   aggregate = Seq(macroProvider, macroClient),
	   settings = Defaults.defaultSettings ++ defaultSettings
	)

	lazy val macroProvider = Project(
	   base = file("macro-provider"),
	   id = "macro-provider",
	   settings = Defaults.defaultSettings ++ defaultSettings
	)

	lazy val macroClient = Project(
	   base = file("macro-client"),
	   id = "macro-client",
	   dependencies = Seq(macroProvider),
	   settings = Defaults.defaultSettings ++ defaultSettings
	)
}
