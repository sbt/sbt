import sbt._

object B extends Build
{
	lazy val root = Project("root", file(".")).dependsOn( file("../../plugin") )
}