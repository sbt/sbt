import sbt._

class TestProject(info: ProjectInfo) extends ParentProject(info)
{
	val a = project("a", "Sub project A")
	val b = project("b", "Sub project B")
}