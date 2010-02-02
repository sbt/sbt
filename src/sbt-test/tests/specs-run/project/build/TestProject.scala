import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	val specs = "org.scala-tools.testing" %% "specs" % "1.6.1" intransitive()
}