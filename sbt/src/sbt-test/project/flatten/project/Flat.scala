import sbt._
import Keys._
import Configurations.{Compile, Test}

object Flat extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file("."),
		settings = Default.defaultSettings ++ forConfig(Compile, "src") ++ forConfig(Test, "test-src") ++ baseSettings
	)

	def baseSettings = Seq(
		LibraryDependencies += "org.scala-tools.testing" %% "scalacheck" % "1.8" % "test",
		SourceFilter := "*.java" | "*.scala"
	)

	def forConfig(conf: Configuration, name: String) = Default.inConfig(conf)( unpackage(name) )

	def unpackage(name: String) = Seq(
		SourceDirectories := file(name) :: Nil,
		ResourceDirectories :== SourceDirectories,
		Keys.Resources <<= (SourceDirectories, SourceFilter, DefaultExcludes) map {
		 (srcs, filter, excl) => srcs.descendentsExcept(-filter,excl).getFiles.toSeq
		},
		Unpackage <<= (JarPath in PackageSrc, Base) map { (jar, base) =>
			IO.unzip(jar, base / name)
		}
	)

	val Unpackage = TaskKey[Unit]("unpackage-src")
}

