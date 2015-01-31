import sbt._
import Keys._

object build extends Build {
	val defaultSettings = Seq(
		libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ ),
		libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _ )
	)

	lazy val root = Project(
	   base = file("."),
	   id = "macro",
	   aggregate = Seq(macroProvider, macroClient),
	   settings = Defaults.defaultSettings ++ defaultSettings
	)

	lazy val deepHelper = Project(
		base = file("deep-helper"),
		id = "deep-helper",
		settings = Defaults.defaultSettings ++ defaultSettings
	)

	lazy val helper = Project(
		base = file("helper"),
		id = "helper",
		dependencies = Seq(deepHelper),
		settings = Defaults.defaultSettings ++ defaultSettings
	)

	lazy val macroProvider = Project(
	   base = file("macro-provider"),
	   id = "macro-provider",
	   dependencies = Seq(helper),
	   settings = Defaults.defaultSettings ++ defaultSettings
	)

	lazy val macroClient = Project(
	   base = file("macro-client"),
	   id = "macro-client",
	   dependencies = Seq(helper, macroProvider),
	   settings = Defaults.defaultSettings ++ defaultSettings
	)
}