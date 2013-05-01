import sbt._
import Keys._

object TestProject extends Build
{
	lazy val root = Project("root", file(".")) settings(
		ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))),
		libraryDependencies <++= baseDirectory (libraryDeps),
		TaskKey[Unit]("check-forced") <<= check("1.2.14"),
		TaskKey[Unit]("check-depend") <<= check("1.2.13")
	)

	def libraryDeps(base: File) = {
		val slf4j = Seq("org.slf4j" % "slf4j-log4j12" % "1.1.0")  // Uses log4j 1.2.13
		if ((base / "force").exists) slf4j :+ ("log4j" % "log4j" % "1.2.14" force()) else slf4j
	}

	def check(ver: String) =
		(dependencyClasspath in Compile) map { jars =>
			val log4j = jars map (_.data) collect {
				case f if f.getName contains "log4j-" => f.getName
			}
			if (log4j.size != 1 || !log4j.head.contains(ver))
  			error("Did not download the correct jar.")
		}
}
