import sbt._

class TestQuickProject(info: ProjectInfo) extends DefaultProject(info) {
	val snapshots = ScalaToolsSnapshots
	val specs = "org.scala-tools.testing" %% "specs" % "1.6.1"
}
