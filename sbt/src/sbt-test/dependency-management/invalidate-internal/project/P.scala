import sbt._
import Keys._

object P extends Build
{
	lazy val root = Project("root", file("."))
	lazy val a = Project("a", file("a")) dependsOn(b)
	lazy val b = Project("b", file("b")) 
}	