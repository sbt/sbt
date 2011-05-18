import sbt._

trait Sxr extends BasicScalaProject
{
	val sxrConf = config("sxr") hide
	val sxrDep = "org.scala-tools.sxr" %% "sxr" % "0.2.7" % sxrConf.name jar()

	def deepSources: PathFinder
	def deepBaseDirectories: PathFinder
	def sxrBaseDirs = "-P:sxr:base-directory:" + deepBaseDirectories.absString
	def sxrLocation = "-Xplugin:" + managedClasspath(sxrConf).absString
	def sxrDirName = "browse"
	def sxrOutput = outputPath / (sxrDirName + ".sxr")
	def sxrClassesOutput = outputPath / sxrDirName // isn't actually written to, since compiler stops before writing classes
	def sxrOptions = compileOptions.map(_.asString) ++ Seq(sxrBaseDirs, sxrLocation, "-Ystop:superaccessors")

	lazy val sxr = task {
		xsbt.FileUtilities.delete(sxrOutput +++ sxrClassesOutput getFiles)
		xsbt.FileUtilities.createDirectory(sxrClassesOutput asFile)
		val compiler = new xsbt.RawCompiler(buildScalaInstance, xsbt.ClasspathOptions.auto, log)
		compiler(deepSources.getFiles, compileClasspath.getFiles, sxrClassesOutput asFile, sxrOptions)
		None
	}
}
