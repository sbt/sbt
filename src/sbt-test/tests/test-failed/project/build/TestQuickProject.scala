import sbt._

class TestQuickProject(info: ProjectInfo) extends DefaultProject(info) {
	val snapshots = ScalaToolsSnapshots
    val scalatest = "org.scalatest" % "scalatest" % "1.0.1-for-scala-2.8.0.Beta1-RC1-with-test-interfaces-0.2-SNAPSHOT"
}
