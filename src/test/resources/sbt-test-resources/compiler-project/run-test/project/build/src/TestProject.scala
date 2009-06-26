import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	override def useMavenConfigurations = true
	val sc = "org.scala-tools.testing" % "scalacheck" % "1.5" % "test->default"
}
