import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")

	override def managedStyle = ManagedStyle.Ivy
	def projectRoot = Path.fromFile(info.projectPath.asFile.getParentFile.getParentFile)
	val local = Resolver.file("test-repo", (projectRoot /"repo" / "test").asFile)

	val plug = "test" % "plugins-test" % "1.0"
}
