import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info) with Marker
{
	lazy val interactiveTest = interactiveTask { mark() }
}