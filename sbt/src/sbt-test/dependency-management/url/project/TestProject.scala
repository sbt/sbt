	import sbt._
	import Keys._

object TestProject extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file(".")) settings(
		libraryDependencies += "slinky" % "slinky" % "2.1" % "test" from "http://slinky2.googlecode.com/svn/artifacts/2.1/slinky.jar",
		TaskKey("check-in-test") <<= checkClasspath(Test),
		TaskKey("check-in-compile") <<= checkClasspath(Compile)
	)
		
	lazy val checkInTest = checkClasspath(testClasspath)
	lazy val checkInCompile = checkClasspath(compileClasspath)
	private def checkClasspath(conf: Configuration) =
		fullClasspath in conf map { cp =>
			try
			{
				val loader = ClasspathUtilities.toLoader(cp)
				Class.forName("slinky.http.Application", false, loader)
			}
			catch
			{
				case _: ClassNotFoundException => error("Dependency not downloaded.") 
			}
		}
}