import sbt._
import Keys._

object B extends Build {
	lazy val b = Project("b", file("b"))
	lazy val a = Project("a", file("a"))
}
