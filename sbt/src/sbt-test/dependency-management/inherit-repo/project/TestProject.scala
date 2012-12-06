	import sbt._
	import Keys._

object TestProject extends Build
{
	override lazy val settings = super.settings ++ Seq(
		externalResolvers := Nil,
		autoScalaLibrary := false,
		ivyScala := None,
		ivyPaths <<= baseDirectory( dir => new IvyPaths(dir, Some(dir / "ivy-home")))
	)

	lazy val root = Project("root", file(".")) aggregate(a, b)

	lazy val a = Project("a", file("a")) delegateTo(b) settings(
		libraryDependencies += "com.camptocamp.tl.caltar" % "core" % "0.5" intransitive()
	)
	lazy val b = Project("b", file("b"))
}