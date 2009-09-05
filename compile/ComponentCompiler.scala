package xsbt

import java.io.File

object ComponentCompiler
{
	val xsbtiID = "xsbti"
	val srcExtension = "-src"
	val binSeparator = "-bin_"
	val compilerInterfaceID = "compiler-interface"
	val compilerInterfaceSrcID = compilerInterfaceID + srcExtension
}
class ComponentCompiler(scalaVersion: String, compiler: RawCompiler, manager: ComponentManager)
{
	import ComponentCompiler._
	import FileUtilities.{copy, createDirectory, zip, jars, unzip, withTemporaryDirectory}
	def apply(id: String): File =
	{
		val binID = binaryID(id, scalaVersion)
		try { manager.file(binID) }
		catch { case e: InvalidComponent => compileAndInstall(id, binID) }
	}
	private def binaryID(id: String, scalaVersion: String) = id + binSeparator + scalaVersion
	private def compileAndInstall(id: String, binID: String): File =
	{
		val srcID = id + srcExtension
		val binaryDirectory = manager.location(binID)
		createDirectory(binaryDirectory)
		val targetJar = new File(binaryDirectory, id + ".jar")
		compileSources(manager.files(srcID), targetJar, id)
		manager.cache(binID)
		targetJar
	}
	/** Extract sources from source jars, compile them with the xsbti interfaces on the classpath, and package the compiled classes and
	* any resources from the source jars into a final jar.*/
	private def compileSources(sourceJars: Iterable[File], targetJar: File, id: String)
	{
		import Paths._
		withTemporaryDirectory { dir =>
			val extractedSources = (Set[File]() /: sourceJars) { (extracted, sourceJar)=> extracted ++ unzip(sourceJar, dir) }
			val (sourceFiles, resources) = extractedSources.partition(_.getName.endsWith(".scala"))
			withTemporaryDirectory { outputDirectory =>
				val xsbtiJars = manager.files(xsbtiID)
				compiler(Set() ++ sourceFiles, Set() ++ xsbtiJars, outputDirectory, Nil, true)
				copy(resources x (FileMapper.rebase(dir, outputDirectory)))
				zip((outputDirectory ***) x (PathMapper.relativeTo(outputDirectory)), targetJar)
			}
		}
	}
}