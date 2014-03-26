import sbt._
import Keys._

object B extends Build {
	lazy val root = Project("root", file(".")).settingSets(
		AddSettings.autoPlugins,
		AddSettings.sbtFiles( file("other.sbt") )) // ignore build.sbt
}
