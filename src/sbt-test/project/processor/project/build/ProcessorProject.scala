import sbt._

class PTest(info: ProjectInfo) extends ProcessorProject(info)
{
	val launcherJar = "org.scala-tools.sbt" % "launcher" % info.app.id.version % "test"
	val specs = "org.scala-tools.testing" % "specs" % "1.6.0" % "test"
	override def testClasspath = super.testClasspath +++ info.sbtClasspath
}