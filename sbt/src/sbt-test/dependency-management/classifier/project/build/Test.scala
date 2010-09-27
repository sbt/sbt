import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")
	val testng = "org.testng" % "testng" % "5.7" classifier "jdk15"
}