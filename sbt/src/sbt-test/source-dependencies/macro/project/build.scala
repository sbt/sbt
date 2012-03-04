import sbt._
import Keys._

object build extends Build {
	val defaultSettings = Seq(
		scalaVersion := "2.10.0-M2",
		scalacOptions += "-Xmacros"
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
