	import sbt._
	import Keys._

object ExcludeScala extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file(".")) settings(
		libraryDependencies <++= baseDirectory(dependencies),
		scalaVersion := "2.8.1",
		autoScalaLibrary <<= baseDirectory(base => !(base / "noscala").exists ),
		scalaOverride <<= fullClasspath in Compile map { cp =>
			val existing = cp.files.filter(_.getName contains "scala-library")
			val loader = classpath.ClasspathUtilities.toLoader(existing)
			// check that the 2.8.1 scala-library is on the classpath and not 2.7.7
			Class.forName("scala.collection.immutable.List", false, loader)
		}
	)
	lazy val scalaOverride = TaskKey[Unit]("scala-override")

	def dependencies(base: File) =
		if( ( base / "sbinary").exists )
			("org.scala-tools.sbinary" % "sbinary_2.7.7" % "0.3") :: Nil
		else
			Nil
}