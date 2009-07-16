import sbt._

class TestQuickProject(info: ProjectInfo) extends DefaultProject(info) {
    val scalatest = "org.scalatest" % "scalatest" % "0.9.3"
}
