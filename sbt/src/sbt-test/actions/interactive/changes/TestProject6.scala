import sbt._

class TestProject(info: ProjectInfo) extends ParentProject(info)
{
	val subA = project("a", "A", new SubA(_))
	lazy val interactiveTest = task { Some("Parent task should not be called") }
	
	class SubA(info: ProjectInfo) extends DefaultProject(info)
	{
		lazy val interactiveTest = interactiveTask { Some("Child task should not be called.") }
	}
}