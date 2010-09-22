import sbt._

class Retrieve(info: ProjectInfo) extends ParentProject(info)
{
	override def ivyCacheDirectory = Some(outputPath / "ivy" / "cache")
	override def ivyRepositories = Resolver.file("local", "ivy" / "local" asFile)(Resolver.ivyStylePatterns) :: Nil

	override def libraryDependencies = Set() ++( if("mavenStyle".asFile.exists) mavenStyleDependencies else autoStyleDependencies )

	def autoStyleDependencies = parentDep("A") :: subDep("A") :: subDep("B") ::parentDep("D") :: Nil
	def mavenStyleDependencies = parentDep("B") :: parentDep("C") :: subDep("C") :: subDep("D") :: Nil

	def parentDep(org: String) =  org %% "publish-test" % "1.0"
	def subDep(org: String) = org %% "sub-project" % "1.0"
}
