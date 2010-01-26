import sbt._

class CompatScalaTest(info: ProjectInfo) extends DefaultProject(info)
{
	val specs = "org.scala-tools.testing" % "scalacheck" % "1.6" % "test"
}