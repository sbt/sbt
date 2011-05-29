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
		unmanagedSourceDirectories <<= baseDirectory( base => (base / name) :: Nil ),
		defaultExcludes in unmanagedResources <<= sourceFilter.identity,
		unmanagedResourceDirectories <<= unmanagedSourceDirectories.identity,
		unpackage <<= (artifactPath in packageSrc, baseDirectory) map { (jar, base) =>
			IO.unzip(jar, base / name)
		}
	)

	val unpackage = TaskKey[Unit]("unpackage")
}

