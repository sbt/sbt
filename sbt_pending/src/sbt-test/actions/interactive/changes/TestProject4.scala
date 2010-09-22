import sbt._

class TestProject(info: ProjectInfo) extends ParentProject(info)
{
	val subA = project("a", "A", new SubA(_))
	class SubA(info: ProjectInfo) extends DefaultProject(info) with Marker
	{
		lazy val interactiveTest = interactiveTask { mark() }
	}
}