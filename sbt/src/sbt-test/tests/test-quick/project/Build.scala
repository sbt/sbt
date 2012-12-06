import sbt._
import Keys._
import Defaults._

object B extends Build {
	lazy val root = Project("root", file("."), settings = defaultSettings ++ Seq(
		libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test" cross(CrossVersion.full),
		parallelExecution in test := false
	))
}
