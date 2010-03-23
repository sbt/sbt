import sbt._

class MultiPublishTest(info: ProjectInfo) extends ParentProject(info)
{
	override def managedStyle =
		if(path("mavenStyle").exists)
			ManagedStyle.Maven
		else
			ManagedStyle.Auto

	override def deliverProjectDependencies = if(managedStyle == sub.managedStyle) super.deliverProjectDependencies else Nil

	override def ivyCacheDirectory = Some("ivy" / "cache")
	override def ivyRepositories = Resolver.file("local", "ivy" / "local" asFile)(Resolver.ivyStylePatterns) :: Nil
	
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