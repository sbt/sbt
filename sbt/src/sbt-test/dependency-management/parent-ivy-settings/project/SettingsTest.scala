	import sbt._
	import Keys._

object SettingsTest extends Build
{
	lazy val parent: Project = Project("Parent", file(".")) aggregate(sub) settings(
		externalIvySettings(),
		ivyPaths <<= baseDirectory( dir => new IvyPaths(dir, Some(dir / "ivy-home")))
	)
	lazy val sub: Project = Project("Sub", file("sub"), delegates = parent :: Nil) dependsOn(compiler) aggregate(compiler) settings(
		externalIvySettings()
	)
	lazy val compiler = Project("Compiler", file("compiler"), delegates = sub :: Nil) settings(
		libraryDependencies <+= scalaVersion( "org.scala-lang" % "scala-compiler" % _ )
	)
}