import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
	def ivyCacheDirectory = outputPath / "ivy-cache"
	override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList

	override def managedStyle = ManagedStyle.Ivy
	def projectRoot = Path.fromFile(info.projectPath.asFile.getParentFile.getParentFile)
	val publishTo = Resolver.file("test-repo", (projectRoot /"repo" / "test").asFile)

	val plug = "test" % "plugins-test" % "1.0"
}