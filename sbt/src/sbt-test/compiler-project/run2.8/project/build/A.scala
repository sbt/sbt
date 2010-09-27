import sbt._

class A(info: ProjectInfo) extends DefaultProject(info)
{
	val snaps = ScalaToolsSnapshots
	val specs28 = "org.scala-tools.testing" %% "specs" % "1.6.5-SNAPSHOT" % "test"
}