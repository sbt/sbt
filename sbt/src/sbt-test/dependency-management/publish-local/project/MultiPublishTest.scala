	import sbt._
	import Keys._

object MultiPublishTest extends Build
{
	override lazy val settings = super.settings ++ Seq(
		organization := "A",
		version := "1.0",
		ivyPaths <<= baseDirectory( dir => new IvyPaths(dir, Some(dir / "ivy" / "cache")) ),
		externalResolvers <<= baseDirectory { base => Resolver.file("local", base / "ivy" / "local" asFile)(Resolver.ivyStylePatterns) :: Nil }
	)

	lazy val root = Project("root", file(".")) settings( mavenStyle, name = "Publish Test" )

	lazy val sub = Project("sub", file("sub")) settings( mavenStyle, name = "Sub Project" )

	lazy val mavenStyle = publishMavenStyle <<= baseDirectory { base => (base / "mavenStyle") exists }

	override def deliverProjectDependencies = if(managedStyle == sub.managedStyle) super.deliverProjectDependencies else Nil
}