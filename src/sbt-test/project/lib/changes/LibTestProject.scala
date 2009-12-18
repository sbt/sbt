import sbt._

class LibTestProject(info: ProjectInfo) extends DefaultProject(info)
{
	override def disableCrossPaths = true
	override def buildScalaVersion = defScalaVersion.value
	lazy val useJar = task { injar.Test.foo }
}