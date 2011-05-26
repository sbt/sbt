import sbt._
import Keys._

object Test extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file(".")) settings(
		libraryDependencies += "net.liftweb" % "lift-webkit" % "1.0" intransitive(),
		libraryDependencies += "org.scalacheck" % "scalacheck" % "1.5" intransitive(),
		transitiveClassifiers := Seq("sources"),
		TaskKey("check-sources") <<= updateClassifiers map checkSources,
		TaskKey("check-binaries") <<= update map checkBinaries
	)
	def getSources(report: UpdateReport)  = report.matching(artifactFilter(`classifier` = "sources") )
	def checkSources(report: UpdateReport) =
	{
		val srcs = getSources(report)
		if(srcs.isEmpty) error("No sources retrieved") else if(srcs.size != 2) error("Incorrect sources retrieved:\n\t" + srcs.mkString("\n\t"))
	}
	def checkBinaries(report: UpdateReport) =
	{
		val srcs = getSources(report)
		if(!srcs.isEmpty) error("Sources retrieved:\n\t" + srcs.mkString("\n\t"))
	}
}