import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	override def compileOrder = CompileOrder.JavaThenScala
}