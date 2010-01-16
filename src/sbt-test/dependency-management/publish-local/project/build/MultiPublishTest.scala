import sbt._

class MultiPublishTest(info: ProjectInfo) extends ParentProject(info)
{
	override def managedStyle =
		if(path("mavenStyle").exists)
			ManagedStyle.Maven
		else
			ManagedStyle.Auto
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")
	
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