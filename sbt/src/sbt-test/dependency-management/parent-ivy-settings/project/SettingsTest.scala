	import sbt._
	import Keys._

object SettingsTest extends Build
{
	lazy val projects = Seq(parent, sub, configgy)
	lazy val parent: Project = Project("Parent", file(".")) aggregate(sub) settings(
		externalIvySettings(),
		ivyPaths <<= baseDirectory( dir => new IvyPaths(dir, Some(dir / "ivy-home")))
	)
	lazy val sub: Project = Project("Sub", file("sub"), delegates = parent :: Nil) dependsOn(configgy) aggregate(configgy) settings(
		externalIvySettings()
	)
	lazy val configgy = Project("Configgy", file("configgy"), delegates = sub :: Nil) settings(
		libraryDependencies += "net.lag" % "configgy" % "1.1"
	)
}