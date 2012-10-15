	import sbt._
	import Keys._

object ExcludeScala extends Build
{
	lazy val root = Project("root", file(".")) settings(
		libraryDependencies <++= baseDirectory(dependencies),
		scalaVersion := "2.9.2",
		autoScalaLibrary <<= baseDirectory(base => !(base / "noscala").exists ),
		scalaOverride <<= check("scala.App")
	)
	def check(className: String): Project.Initialize[Task[Unit]] = fullClasspath in Compile map { cp =>
		val existing = cp.files.filter(_.getName contains "scala-library")
		println("Full classpath: " + cp.mkString("\n\t", "\n\t", ""))
		println("scala-library.jar: " + existing.mkString("\n\t", "\n\t", ""))
		val loader = classpath.ClasspathUtilities.toLoader(existing)
		Class.forName(className, false, loader)
	}

	lazy val scalaOverride = TaskKey[Unit]("scalaOverride", "Check that the proper version of Scala is on the classpath.")

	def dependencies(base: File) =
		if( ( base / "stm").exists )
			("org.scala-tools" % "scala-stm_2.8.2" % "0.6") :: Nil
		else
			Nil
}
