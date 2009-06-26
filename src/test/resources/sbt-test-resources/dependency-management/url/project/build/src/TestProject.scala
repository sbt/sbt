import sbt._

import java.net.{URL, URLClassLoader}

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	override def useMavenConfigurations = true
	val direct = "slinky" % "slinky" % "2.1" % "test->default" from "http://slinky2.googlecode.com/svn/artifacts/2.1/slinky.jar"
	lazy val checkInTest = checkClasspath(testClasspath)
	lazy val checkInCompile = checkClasspath(compileClasspath)
	private def checkClasspath(cp: PathFinder) =
		task
		{
			try
			{
				Class.forName("slinky.http.Application", false, new URLClassLoader(cp.get.map(_.asURL).toList.toArray))
				None
			}
			catch
			{
				case _: ClassNotFoundException =>
					Some("Dependency not downloaded.") 
			}
		}
}