import sbt._

class TestProject2(info: ProjectInfo) extends DefaultProject(info)
{
	override def testFrameworks = framework.FrameworkScalaCheck :: Nil
	override def useMavenConfigurations = true	
	val sc = "org.scala-tools.testing" % "scalacheck" % "1.5" % "test->default"
}
