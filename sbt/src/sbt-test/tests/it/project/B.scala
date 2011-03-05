import sbt._
import Keys._

object B extends Build
{
	lazy val projects = Seq(root)
	lazy val root =
		Project("root", file("."))
			.configs( it )
			.settings( LibraryDependencies += specs )
			.settings( Defaults.itSettings : _*)

	lazy val it = Configurations.IntegrationTest
	lazy val specs = "org.scala-tools.testing" %% "specs" % "1.6.7.2" % "it,test" intransitive()
}