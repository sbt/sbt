import sbt._

class MultiPublishTest(info: ProjectInfo) extends ParentProject(info)
{
	override def managedStyle =
		if(path("mavenStyle").exists)
			ManagedStyle.Maven
		else
			ManagedStyle.Auto
	def ivyCacheDirectory = outputPath / "ivy-cache"
	override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList
	
	lazy val sub = project("sub", "Sub Project", new SubProject(_))
	class SubProject(info: ProjectInfo) extends DefaultProject(info)
	{
		override def managedStyle =
			if(path("mavenStyle").exists)
				ManagedStyle.Maven
			else
				ManagedStyle.Auto
	}
}