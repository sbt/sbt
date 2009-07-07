import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	override def useMavenConfigurations = true
	val st = "org.scala-tools.testing" % "scalatest" % "0.9.5" % "test->default"
}