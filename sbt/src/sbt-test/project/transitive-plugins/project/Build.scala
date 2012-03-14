import sbt._
import Keys._

object Build extends Build
{
	override def settings = super.settings ++ Seq(
		sbtBinaryVersion <<= sbtVersion
	)

	lazy val root = Project("root", file(".")) 
	lazy val a = project("a")
	lazy val b = project("b")
	lazy val c = project("c")
	def project(s: String) = Project(s, file(s)) settings(
		ivyPaths <<= (baseDirectory, target in root)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))),
		resolvers <+= appConfiguration { app =>
			val ivyHome = Classpaths.bootIvyHome(app) getOrElse (file(System.getProperty("user.home")) / ".ivy2")
			Resolver.file("real-local",  ivyHome / "local")(Resolver.ivyStylePatterns)
		}
	)
}