import sbt._

class DefineColor(info: ProjectInfo) extends DefaultProject(info)
{
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")

	override def managedStyle = ManagedStyle.Ivy
	val publishTo = Resolver.file("test-repo", ("repo" / "test").asFile)
	override def projectID = super.projectID extra("e:color" -> "red")
}