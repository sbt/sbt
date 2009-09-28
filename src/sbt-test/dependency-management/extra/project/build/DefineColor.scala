import sbt._

class DefineColor(info: ProjectInfo) extends DefaultProject(info)
{
	def ivyCacheDirectory = outputPath / "ivy-cache"
	override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList

	override def managedStyle = ManagedStyle.Ivy
	val publishTo = Resolver.file("test-repo", ("repo" / "test").asFile)
	override def projectID = super.projectID extra("e:color" -> "red")
}