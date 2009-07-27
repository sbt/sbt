import sbt._
class AnonTest(info: ProjectInfo) extends DefaultProject(info)
{
  override def compileOrder = CompileOrder.JavaThenScala
}