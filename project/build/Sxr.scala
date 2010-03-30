import sbt._

trait Sxr extends BasicScalaProject
{
	val sxrConf = config("sxr") hide
	val sxrDep = "org.scala-tools.sxr" %% "sxr" % "0.2.4" % sxrConf.name

	def deepSources: PathFinder
	def deepBaseDirectories = Path.finder { topologicalSort.flatMap { case p: ScalaPaths => p.mainSourceRoots.getFiles } }
	def sxrBaseDirs = "-P:sxr:base-directory:" + deepBaseDirectories.absString
	def sxrLocation = "-Xplugin:" + managedClasspath(sxrConf).absString
	def sxrDirName = "browse"
	def sxrOutput = outputPath / (sxrDirName + ".sxr")
	def sxrClassesOutput = outputPath / sxrDirName // isn't actually written to, since compiler stops before writing classes
	def sxrOptions = compileOptions.map(_.asString) ++ Seq(sxrBaseDirs, sxrLocation, "-Ystop:superaccessors")

	lazy val sxr = task {
		xsbt.FileUtilities.delete(sxrOutput +++ sxrClassesOutput getFiles)
		xsbt.FileUtilities.createDirectory(sxrClassesOutput asFile)
		val compiler = new xsbt.RawCompiler(buildScalaInstance, true, true, log)
		// `Set() ++` is temporary.  getFiles returns immutable.Set in sbt 0.7.3.
		compiler(Set() ++ deepSources.getFiles, Set() ++ compileClasspath.getFiles, sxrClassesOutput asFile, sxrOptions)
		None
	}
}
