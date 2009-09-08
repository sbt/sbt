import sbt._

class UseColor(info: ProjectInfo) extends DefaultProject(info)
{
	def color = FileUtilities.readString("color".asFile, log).right.getOrElse(error("No color specified"))
	override def libraryDependencies = Set(
		"org.scala-tools.sbt" % "test-ivy-extra" %"1.0" extra("e:color" -> color)
	)
}