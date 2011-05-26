	import sbt._
	import Keys._

object TestProject extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file(".")) settings(
		ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))),
		libraryDependencies <+= baseDirectory(transitive("javax.mail" % "mail" % "1.4.1")),
		TaskKey("check-transitive") <<= check(true),
		TaskKey("check-intransitive") <<= check(false)
	)

	def transitive(dep: ModuleID)(base: File) =
		if((base / "transitive").exists) dep else dep.intransitive()

	private def check(transitive: Boolean) =
		(dependencyClasspath in Compile) map { downloaded =>
			val jars = downloaded.size
			if(transitive)
				if(jars <= 2) error("Transitive dependencies not downloaded") else ()
			else
				if(jars > 2) error("Transitive dependencies downloaded (" + downloaded.files.mkString(", ") + ")") else ()
		}
}
