import sbt._

class UnmanagedTest(info: ProjectInfo) extends DefaultWebProject(info)
{
	override def disableCrossPaths = true
	override def webappUnmanaged = temporaryWarPath / "WEB-INF" / "appengine-generated" ***
}