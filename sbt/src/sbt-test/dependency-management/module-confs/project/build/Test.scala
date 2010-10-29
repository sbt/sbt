import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")
	
	def snapshotPattern = "http://scala-tools.org/repo-snapshots/[organization]/[module]/2.9.0-SNAPSHOT/[artifact]-[revision].[ext]"
	def scalaSnapshots = Resolver.url("Scala Tools Snapshots") artifacts(snapshotPattern) ivys(snapshotPattern) mavenStyle()
	val scOnly = ModuleConfiguration("org.scala-lang", "*", "2.9.0-.*", scalaSnapshots)

	val uniqueScala = "org.scala-lang" % "scala-compiler" % "2.9.0-20101028.010428-38"
	val otherDep = "org.scala-tools.sxr" % "sxr_2.7.5" % "0.2.3"
	
	override def checkExplicitScalaDependencies = false
}