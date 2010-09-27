import sbt._

class CompatSpecsTest(info: ProjectInfo) extends DefaultProject(info)
{
	val specs = "org.scala-tools.testing" % "specs" % "1.6.0" % "test"
}