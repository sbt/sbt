import sbt._
import Keys._
import Defaults._

object B extends Build {
	lazy val root = Project("root", file("."), settings = defaultSettings ++ Seq(
		libraryDependencies += "org.scalatest" % "scalatest_2.9.0" % "1.6.1" % "test",
		parallelExecution in test := false
	))
}
