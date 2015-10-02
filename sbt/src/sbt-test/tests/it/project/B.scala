	import sbt._
	import Keys._
  import Import._

object B extends Build
{
	lazy val IntegrationTest = config("it") extend(Test)
	lazy val root =
		Project("root", file("."))
			.configs( IntegrationTest )
			.settings( Defaults.itSettings : _*)
			.settings( libraryDependencies += specs )

	lazy val specs = "org.specs2" % "specs2_2.10" % "1.12.3" % "test"
}
