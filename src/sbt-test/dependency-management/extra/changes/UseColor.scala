import sbt._

class UseColor(info: ProjectInfo) extends DefaultProject(info)
{
	def ivyCacheDirectory = outputPath / "ivy-cache"
	override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList

	override def managedStyle = ManagedStyle.Ivy
	val repo = Resolver.file("test-repo", ("repo" / "test").asFile)
	def color = FileUtilities.readString("color".asFile, log).right.getOrElse(error("No color specified"))
	override def libraryDependencies = Set(
		"org.scala-tools.sbt" % "test-ivy-extra" %"1.0" extra("e:color" -> color)
	)
}