import sbt._

class LibTestProject(info: ProjectInfo) extends DefaultProject(info)
{
	lazy val useJar = task { injar.Test.foo }
}