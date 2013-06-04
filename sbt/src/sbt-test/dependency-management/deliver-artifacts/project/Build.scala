import sbt._
import Keys._

object B extends Build {

	override def settings = super.settings ++ Seq(
		organization in ThisBuild := "org.example",
		version in ThisBuild := "1.0"
	)
	
	lazy val a = Project("a", file("a")).settings(common: _*).settings(
		// verifies that a can be published as an ivy.xml file and preserve the extra artifact information,
		//   such as a classifier
		libraryDependencies := Seq("net.sf.json-lib" % "json-lib" % "2.4" classifier "jdk15" intransitive()),
		// verifies that an artifact without an explicit configuration gets published in all public configurations
		artifact in (Compile,packageBin) := Artifact("demo")
	)
	
	lazy val b = Project("b", file("b")).settings(common: _*).settings(
		libraryDependencies <<= (organization, version) { (o,v) => Seq(o %% "a" % v) }
	)
	
	lazy val common = Seq(
		autoScalaLibrary := false, // avoid downloading fresh scala-library/scala-compiler
		ivyPaths <<= (baseDirectory in ThisBuild, target in LocalRootProject) { (base, t) => new IvyPaths(base, Some(t / "ivy-cache")) }
	)
}