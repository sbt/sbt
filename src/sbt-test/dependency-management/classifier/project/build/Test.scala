import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	def ivyCacheDirectory = outputPath / "ivy-cache"
	override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList
	val testng = "org.testng" % "testng" % "5.7" classifier "jdk15"
}