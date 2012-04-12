import sbt._
import Keys._

object Build extends Build
{
	lazy val a = Project("a", file(".")) settings(externalIvySettings()) dependsOn(b)
	lazy val b = Project("b", file("b")) settings(externalIvySettings( (baseDirectory in ThisBuild) / "ivysettings.xml" ))
}