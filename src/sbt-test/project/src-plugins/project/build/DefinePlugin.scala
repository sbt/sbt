import sbt._

class DefinePlugin(info: ProjectInfo) extends PluginProject(info)
{
	def ivyCacheDirectory = outputPath / "ivy-cache"
	override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList

	override def managedStyle = ManagedStyle.Ivy
	val publishTo = Resolver.file("test-repo", ("repo" / "test").asFile)
}