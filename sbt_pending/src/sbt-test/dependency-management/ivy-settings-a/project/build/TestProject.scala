import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")
	override def disableCrossPaths = true
}