import sbt._

object B extends Build
{
	lazy val projects = Project("root", file(".")).dependsOn( file("../../plugin") ) :: Nil
}