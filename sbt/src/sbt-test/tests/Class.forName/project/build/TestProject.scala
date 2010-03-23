import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	override def disableCrossPaths = true
}