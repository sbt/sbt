import sbt._

class PomTest(info: ProjectInfo) extends ParentProject(info) with BasicManagedProject
{
	val subJar = project("subJar", "Sub Jar", new DefaultProject(_))
	val subWar = project("subWar", "Sub War", new DefaultWebProject(_))
	val subParent = project("subParent", "Sub Parent", i => new ParentProject(i) with BasicManagedProject)

	def readPom(path: Path) = xml.XML.loadFile(path.asFile)
	lazy val checkPom = task {
		checkPackaging(subJar.pomPath, "jar") orElse
		checkPackaging(subWar.pomPath, "war") orElse
		checkPackaging(subParent.pomPath, "pom") orElse
		checkPackaging(pomPath, "pom")
	}
	def checkPackaging(pom: Path, expected: String) =
	{
		val packaging = (readPom(pom) \\ "packaging").text
		if(packaging == expected) None else Some("Incorrect packaging for '" + pom + "'.  Expected '" + expected + "', but got '" + packaging + "'")
	}
}