import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	val scalacheck = "org.scalacheck" % "scalacheck" % "1.5"
	val cacheDirectory = outputPath / "cache"
}