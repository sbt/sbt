import sbt._
import Keys._

object PomTest extends Build
{
	override def settings = super.settings :+ (TaskKey[Unit]("check-pom") <<= checkPom)

	lazy val subJar = Project("Sub Jar", file("subJar"))
	lazy val subWar = Project("Sub War", file("subWar")) settings( warArtifact)
	lazy val subParent = Project("Sub Parent", file("subParent")) settings( publishArtifact in Compile := false )

	def art(p: ProjectReference) = makePom in p
	def checkPom = (art(subJar), art(subWar), art(subParent)) map { (jar, war, pom) =>
		checkPackaging(jar, "jar")
		checkPackaging(war, "war")
		checkPackaging(pom, "pom")
	}
	def checkPackaging(pom: File, expected: String) =
	{
		val packaging = (xml.XML.loadFile(pom) \\ "packaging").text
		if(packaging != expected) error("Incorrect packaging for '" + pom + "'.  Expected '" + expected + "', but got '" + packaging + "'")
	}
	def warArtifact = artifact in (Compile, packageBin) ~= { _.copy(`type` = "war", extension = "war") }
}