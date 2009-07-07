import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	val javaNet = JavaNet1Repository
	val ejb = "javax.ejb" % "ejb-api" % "3.0"
}