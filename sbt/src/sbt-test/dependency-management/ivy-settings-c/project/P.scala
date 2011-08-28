import sbt._
import Keys._

object B extends Build
{
	lazy val dep = Project("dep", file("dep")) settings( baseSettings : _*) settings(
		organization := "org.example",
		version := "1.0",
		publishTo <<= baseDirectory in ThisBuild apply { base =>
			Some(Resolver.file("file", base / "repo")(Resolver.ivyStylePatterns))
		}
	)
	lazy val use = Project("use", file("use")) settings(baseSettings : _*) settings(
		libraryDependencies += "org.example" %% "dep" % "1.0",
		externalIvySettings(),
		publishTo <<= baseDirectory { base =>
			Some(Resolver.file("file", base / "repo")(Resolver.ivyStylePatterns))
		},
		TaskKey[Unit]("check") <<= baseDirectory map {base =>
			val inCache = ( (base / "target" / "use-cache") ** "*.jar").get
			assert(inCache.isEmpty, "Cache contained jars: " + inCache)
		}
	)
	lazy val baseSettings = Seq(
		autoScalaLibrary := false,
		unmanagedJars in Compile <++= scalaInstance map (_.jars),
		publishArtifact in packageSrc := false,
		publishArtifact in packageDoc := false,
		publishMavenStyle := false
	)
}