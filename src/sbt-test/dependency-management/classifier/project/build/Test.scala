import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	val testng = "org.testng" % "testng" % "5.7" classifier "jdk15"
}