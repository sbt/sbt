import sbt._
import Import._

object B extends Build
{
	lazy val root = Project("root", file(".")).dependsOn( file("../plugin") )
}