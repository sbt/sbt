import sbt._
import Keys._

object B extends Build
{
	lazy val root = Project("root", file("."))
	lazy val a = Project("a", file("a"))
	lazy val b = Project("b", file("b"))
}
