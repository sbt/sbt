import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	def ivyCacheDirectory = outputPath / "ivy-cache"
	override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList
	
	def snapshotPattern = "http://scala-tools.org/repo-snapshots/[organization]/[module]/2.8.0-SNAPSHOT/[artifact]-[revision].[ext]"
	def scalaSnapshots = Resolver.url("Scala Tools Snapshots") artifacts(snapshotPattern) ivys(snapshotPattern) mavenStyle()
	val scOnly = ModuleConfiguration("org.scala-lang", "*", "2.8.0-.*", scalaSnapshots)

	val uniqueScala = "org.scala-lang" % "scala-compiler" % "2.8.0-20091017.011744-240"
	val otherDep = "org.scala-tools.sxr" % "sxr_2.7.5" % "0.2.3"
}