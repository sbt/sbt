import sbt._

class DefinePlugin(info: ProjectInfo) extends PluginProject(info)
{
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")

	override def managedStyle = ManagedStyle.Ivy
	val publishTo = Resolver.file("test-repo", ("repo" / "test").asFile)
}