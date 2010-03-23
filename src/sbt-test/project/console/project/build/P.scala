import sbt._

class Root(info: ProjectInfo) extends ParentProject(info)
{
	lazy val sub1 = project("sub1", "sub1")
	lazy val sub2 = project("sub2", "sub2", new Sub2(_))
	lazy val sub3 = project("sub3", "sub3", new DefaultProject(_))
	
	class Sub2(info: ProjectInfo) extends DefaultProject(info)
}