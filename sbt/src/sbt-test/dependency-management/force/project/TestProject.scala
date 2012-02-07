import sbt._
import Keys._

object TestProject extends Build
{
	lazy val root = Project("root", file(".")) settings(
		ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))),
		libraryDependencies ++= Seq("log4j" % "log4j" % "1.2.14" force(),
 																"log4j" % "log4j" % "1.2.13"),
		TaskKey[Unit]("check-forced") <<= check()
	)

	def check() =
		(dependencyClasspath in Compile) map { jars =>
			val log4j = jars map (_.data) collect {
				case f if f.getName contains "log4j" => f.getName
			}
			if (log4j.size != 1 || !log4j.head.contains("1.2.14"))
  			error("Did not download the correct jar.")
		}
}
