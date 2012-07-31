import sbt._
import Keys._
import Configurations.{Compile, Test}

object Flat extends Build
{
	lazy val root = Project("root", file("."),
		settings = Defaults.defaultSettings ++ forConfig(Compile, "src") ++ forConfig(Test, "test-src") ++ baseSettings
	)

	def baseSettings = Seq(
		scalaVersion := "2.8.1",
		libraryDependencies += "org.scala-tools.testing" %% "scalacheck" % "1.8" % "test",
		sourceFilter := "*.java" | "*.scala"
	)

	def forConfig(conf: Configuration, name: String) = Project.inConfig(conf)( unpackageSettings(name) )

	def unpackageSettings(name: String) = Seq(
		unmanagedSourceDirectories := (baseDirectory.value / name) :: Nil,
		defaultExcludes in unmanagedResources := sourceFilter.value,
		unmanagedResourceDirectories := unmanagedSourceDirectories.value,
		unpackage := IO.unzip(artifactPath in packageSrc value, baseDirectory.value / name)
	)

	val unpackage = TaskKey[Unit]("unpackage")
}

