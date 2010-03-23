import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	val sourcesOnly = "net.liftweb" % "lift-webkit" % "1.0" % "sources" sources() intransitive()
	val sourcesAndJar = "org.scalacheck" % "scalacheck" % "1.5" withSources()
	def expectedCompile = Set("scalacheck-1.5.jar", "scalacheck-1.5-sources.jar")
	def expectedSources = Set("lift-webkit-1.0-sources.jar")
	lazy val check = task
	{
		val compilePath = names(compileClasspath.get)
		val sources = names(fullClasspath(Configurations.Sources).get)
		if(!same(expectedCompile, compilePath))
			Some("Expected compile classpath " + expectedCompile.mkString(", ") + " differs from actual: " + compilePath.mkString(", "))
		else if(!same(expectedSources, sources))
			Some("Expected sources " + expectedSources.mkString(", ") + " differ from actual: " + sources.mkString(", "))
		else
			None
	}
	def names(path: Iterable[Path]): Set[String] = Set() ++ path.map(_.asFile.getName)
	def same(expected: Set[String], actual: Set[String]) = (expected -- actual).isEmpty
}