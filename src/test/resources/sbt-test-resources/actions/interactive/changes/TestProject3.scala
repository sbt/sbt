import sbt._

class TestProject(info: ProjectInfo) extends ParentProject(info) with Marker
{
	val subA = project("a", "A")
	lazy val interactiveTest = interactiveTask { mark() }
}