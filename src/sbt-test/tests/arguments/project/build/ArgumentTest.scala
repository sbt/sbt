import sbt._

class ArgumentTest(info: ProjectInfo) extends DefaultProject(info)
{
	val snap = ScalaToolsSnapshots
	val st = "org.scalatest" % "scalatest" % "1.0.1-for-scala-2.8.0.Beta1-RC7-with-test-interfaces-0.3-SNAPSHOT"
}