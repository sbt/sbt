import sbt._

class UpdateTestProject(info: ProjectInfo) extends DefaultProject(info)
{
	val sc = "org.scalacheck" % "scalacheck" % "1.5"
}