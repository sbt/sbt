import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	val sc = "org.scalacheck" % "scalacheck" % "1.5"

	override def updateAction = super.updateAction dependsOn addSbt
	lazy val addSbt = task { FileUtilities.copyFile(FileUtilities.sbtJar, (dependencyPath / "sbt.jar").asFile, log) }
}