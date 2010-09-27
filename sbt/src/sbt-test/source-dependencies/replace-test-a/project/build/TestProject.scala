import sbt._
import java.net.URLClassLoader
class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	lazy val checkFirst = checkTask("First")
	lazy val checkSecond = checkTask("Second")
	private def checkTask(className: String) =
		fullClasspath(Configurations.Runtime) map { runClasspath =>
			val cp = runClasspath.map(_.data.toURI.toURL).toArray
			Class.forName(className, false, new URLClassLoader(cp))
		}
}
