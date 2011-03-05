import sbt._
import Keys._
import Configurations.{Compile, Test}

object Flat extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file("."),
		settings = Defaults.defaultSettings ++ forConfig(Compile, "src") ++ forConfig(Test, "test-src") ++ baseSettings
	)

	def baseSettings = Seq(
		libraryDependencies += "org.scala-tools.testing" %% "scalacheck" % "1.8" % "test",
		sourceFilter := "*.java" | "*.scala"
	)

	def forConfig(conf: Configuration, name: String) = Project.inConfig(conf)( unpackageSettings(name) )

	def unpackageSettings(name: String) = Seq(
		sourceDirectories := file(name) :: Nil,
		resourceDirectories :== sourceDirectories,
		resources <<= (sourceDirectories, sourceFilter, defaultExcludes) map {
		 (srcs, filter, excl) => srcs.descendentsExcept(-filter,excl).getFiles.toSeq
		},
		unpackage <<= (jarPath in packageSrc, baseDirectory) map { (jar, base) =>
			IO.unzip(jar, base / name)
		}
	)

	val unpackage = TaskKey[Unit]("unpackage")
}

