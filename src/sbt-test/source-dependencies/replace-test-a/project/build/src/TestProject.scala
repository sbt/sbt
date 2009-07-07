import sbt._
import java.net.URLClassLoader
class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	lazy val checkFirst = checkTask("First")
	lazy val checkSecond = checkTask("Second")
	private def checkTask(className: String) = task { doCheck(className); None }
	private def doCheck(className: String) = Class.forName(className, false, new URLClassLoader(runClasspath.get.map(_.asURL).toList.toArray))
}
