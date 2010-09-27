import sbt._

class CompatScalaTest(info: ProjectInfo) extends DefaultProject(info)
{
	val specs = "org.scalatest" % "scalatest" % "1.0" % "test"
}