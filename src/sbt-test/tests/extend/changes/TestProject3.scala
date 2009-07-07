import sbt._

class TestProject3(info: ProjectInfo) extends ParentProject(info)
{
	lazy val child = project("child", "Main", new ChildProject(_))
	class ChildProject(info: ProjectInfo) extends DefaultProject(info)
	{
		override def testFrameworks = framework.FrameworkScalaCheck :: Nil
		override def useMavenConfigurations = true
		val sc = "org.scala-tools.testing" % "scalacheck" % "1.5" % "test->default"
	}
}
