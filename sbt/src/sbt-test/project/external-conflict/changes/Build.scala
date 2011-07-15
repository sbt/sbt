import sbt._
import Keys._

object B extends Build
{
	override def projectDefinitions(f: File) = Seq( makeProject(f) )

	def makeProject(f: File) =
	{
		val addBin = if(isBinary(f)) binaryDep(baseProject) else baseProject
		if(isSource(f)) sourceDep(addBin) else addBin
	}

	def isBinary(f: File) = f / "binary" exists;
	def isSource(f: File) = f / "source" exists;

	def baseProject = Project("root", file("."))
	def sourceDep(p: Project) = p dependsOn( file("ext") )
	def binaryDep(p: Project) = p settings(
		libraryDependencies += "org.example" %% "app" % "0.1.17",
		resolvers <+= baseDirectory(base => Resolver.file("sample", base / "repo"))
	)
}
