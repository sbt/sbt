import sbt._
import Keys._

object B extends Build
{
	lazy val dep = Project("dep", file("dep")) settings( baseSettings : _*) settings(
		organization := "org.example",
		version := "1.0"
	)
	lazy val use = Project("use", file("use")) dependsOn(dep) settings(baseSettings : _*) settings(
		libraryDependencies += "junit" % "junit" % "4.5",
		externalIvySettings()
	)
	lazy val baseSettings = Seq(
		autoScalaLibrary := false,
		unmanagedJars in Compile <++= scalaInstance map (_.jars)
	)
}
