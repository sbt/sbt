import sbt._

class TestProject(info: ProjectInfo) extends ParentProject(info)
{
	val a = subproject("a", "Sub project A")
	val b = subproject("b", "Sub project B")
	
	private def subproject(path: Path, name: String) = project(path, name, new TestSubProject(_))
	
	class TestSubProject(info: ProjectInfo) extends DefaultProject(info)
}