import sbt._
import Keys._

object B extends Build
{
	lazy val root: Project = Project("root", file(".")) aggregate(logic, ui)
	lazy val logic: Project = Project("logic", file("logic"), delegates = root :: Nil)
	lazy val ui: Project = Project("ui", file("ui"), delegates = root :: Nil) dependsOn(logic)
}