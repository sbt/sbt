import sbt._
import Keys._

object Build extends Build
{
	lazy val root = Project("root", file(".")) 
	lazy val a = project("a")
	lazy val b = project("b")
	lazy val c = project("c")
	def project(s: String) = Project(s, file(s)) settings(
		ivyPaths <<= (baseDirectory in root, target in root)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))),
		resolvers <+= appConfiguration { app => // need this to resolve sbt
			val ivyHome = Classpaths.bootIvyHome(app) getOrElse error("Launcher did not provide the Ivy home directory.")
			Resolver.file("real-local",  ivyHome / "local")(Resolver.ivyStylePatterns)
		},
		resolvers += Resolver.typesafeIvyRepo("releases") // not sure why this isn't included by default
	)
}