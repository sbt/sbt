package xsbt

import java.io.File

object ComponentCompiler
{
	val xsbtiID = "xsbti"
	val compilerInterfaceID = "compilerInterface"
}
class ComponentCompiler(compiler: Compiler)
{
	import compiler.{manager, scalaVersion}
	import ComponentCompiler._
	
	lazy val xsbtiJars = manager.files(xsbtiID)
	
	import FileUtilities.{copy, createDirectory, zip, jars, unzip, withTemporaryDirectory}
	def apply(id: String): File =
	{
		val binID = id + "-bin_" + scalaVersion
		try { manager.file(binID) }
		catch { case e: Exception => compileAndInstall(id, binID) }
	}
	private def compileAndInstall(id: String, binID: String): File =
	{
		val srcID = id + "-src_" + scalaVersion
		val binaryDirectory = manager.location(binID)
		createDirectory(binaryDirectory)
		val targetJar = new File(binaryDirectory, id + ".jar")
		compileSources(manager.files(srcID), compiler, targetJar, id)
		manager.cache(binID)
		targetJar
	}
	/** Extract sources from source jars, compile them with the xsbti interfaces on the classpath, and package the compiled classes and
	* any resources from the source jars into a final jar.*/
	private def compileSources(sourceJars: Iterable[File], compiler: Compiler, targetJar: File, id: String)
	{
		withTemporaryDirectory { dir =>
			val extractedSources = (Set[File]() /: sourceJars) { (extracted, sourceJar)=> extracted ++ unzip(sourceJar, dir) }
			val (sourceFiles, resources) = extractedSources.partition(_.getName.endsWith(".scala"))
			withTemporaryDirectory { outputDirectory =>
				val arguments = Seq("-d", outputDirectory.getAbsolutePath, "-cp", xsbtiJars.mkString(File.pathSeparator)) ++ sourceFiles.toSeq.map(_.getAbsolutePath)
				compiler.raw(arguments)
				copy(resources, outputDirectory, PathMapper.relativeTo(dir))
				zip(Seq(outputDirectory), targetJar, true, PathMapper.relativeTo(outputDirectory))
			}
		}
	}
}