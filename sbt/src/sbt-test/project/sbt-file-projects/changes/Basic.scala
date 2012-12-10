import sbt._
import Keys._

object B extends Build {
	lazy val root = Project("root", file("."))
}