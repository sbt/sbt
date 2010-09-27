import sbt._

class TestProject(info: ProjectInfo) extends ParentProject(info) with Marker
{
	val subA = project("a", "A", new SubA(_))
	lazy val interactiveTest = task { mark() }
	
	class SubA(info: ProjectInfo) extends DefaultProject(info) with Marker
	{
		lazy val interactiveTest = task { mark() }
	}
}