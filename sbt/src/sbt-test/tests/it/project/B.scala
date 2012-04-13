	import sbt._
	import Keys._

object B extends Build
{
	lazy val root =
		Project("root", file("."))
			.configs( IntegrationTest )
			.settings( Defaults.itSettings : _*)
			.settings(
				libraryDependencies += specs,
				resolvers += ScalaToolsReleases
			)

	lazy val specs = "org.specs2" % "specs2_2.9.1" % "1.7.1" % "it,test"
}