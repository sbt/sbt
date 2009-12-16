import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	override def disableCrossPaths = true
}