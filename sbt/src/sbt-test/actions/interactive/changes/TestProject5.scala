import sbt._

class TestProject(info: ProjectInfo) extends ParentProject(info) with Marker
{
	val subA = project("a", "A", new SubA(_))
	lazy val interactiveTest = interactiveTask { mark() }
	
	class SubA(info: ProjectInfo) extends DefaultProject(info)
	{
		lazy val interactiveTest = interactiveTask { Some("Child interactive task should not be called.") }
	}
}