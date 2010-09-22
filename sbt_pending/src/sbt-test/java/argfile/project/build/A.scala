import sbt._

class A(info: ProjectInfo) extends DefaultProject(info)
{
	override def mainScalaSourcePath = sourcePath / " scala test "
	override def mainJavaSourcePath = sourcePath / " java test "
}