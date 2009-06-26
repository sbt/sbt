import sbt._

class FlatProject(info: ProjectInfo) extends DefaultProject(info)
{
	override def useMavenConfigurations = true
	val sc = "org.scalacheck" % "scalacheck" % "1.5" % "test->default"

	def sourceFilter =  "*.java" | "*.scala"
	override def mainSources = descendents(sourcePath ##, sourceFilter)
	override def mainResources = descendents(sourcePath ##, -sourceFilter)

	override def testSourcePath = "test-src"
	override def testSources = descendents(testSourcePath ##, sourceFilter)
	override def testResources = descendents(testSourcePath ##, -sourceFilter)

	lazy val unpackageProject =
		task
		{
			FileUtilities.unzip(outputPath / (artifactBaseName + "-project.zip"), info.projectPath, "src/*", log).left.toOption
		} dependsOn(cleanSrc)

	lazy val cleanSrc = cleanTask(sourcePath +++ testSourcePath)
}
