import sbt._

class ExcludeScala(info: ProjectInfo) extends DefaultProject(info)
{
	lazy val noScala = task { checkNoScala }

	def checkNoScala =
	{
		val existing = compileClasspath.filter(isScalaLibrary _).get
		if(existing.isEmpty) None else Some("Scala library was incorrectly retrieved: " + existing)
	}
	def isScalaLibrary(p: Path) = p.name contains "scala-library"

	val sbinary = "org.scala-tools.sbinary" % "sbinary_2.7.7" % "0.3"
}