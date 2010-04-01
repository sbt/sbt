package xsbt

import java.io.File
import sbt.{ComponentManager, IfMissing}

object ComponentCompiler
{
	val xsbtiID = "xsbti"
	val srcExtension = "-src"
	val binSeparator = "-bin_"
	val compilerInterfaceID = "compiler-interface"
	val compilerInterfaceSrcID = compilerInterfaceID + srcExtension
	val javaVersion = System.getProperty("java.class.version")
}
/** This class provides source components compiled with the provided RawCompiler.
* The compiled classes are cached using the provided component manager according
* to the actualVersion field of the RawCompiler.*/
class ComponentCompiler(compiler: RawCompiler, manager: ComponentManager)
{
	import ComponentCompiler._
	import FileUtilities.{copy, createDirectory, zip, jars, unzip, withTemporaryDirectory}
	def apply(id: String): File =
	{
		val binID = binaryID(id)
		manager.file(binID)( new IfMissing.Define(true, compileAndInstall(id, binID)) )
	}
	def clearCache(id: String): Unit = manager.clearCache(binaryID(id))
	protected def binaryID(id: String) = id + binSeparator + compiler.scalaInstance.actualVersion + "__" + javaVersion
	protected def compileAndInstall(id: String, binID: String)
	{
		val srcID = id + srcExtension
		withTemporaryDirectory { binaryDirectory =>
			val targetJar = new File(binaryDirectory, id + ".jar")
			compileSources(manager.files(srcID)(IfMissing.Fail), targetJar, id)
			manager.define(binID, Seq(targetJar))
		}
	}
	/** Extract sources from source jars, compile them with the xsbti interfaces on the classpath, and package the compiled classes and
	* any resources from the source jars into a final jar.*/
	private def compileSources(sourceJars: Iterable[File], targetJar: File, id: String)
	{
		val isSource = (f: File) => isSourceName(f.getName)
		def keepIfSource(files: Set[File]): Set[File] = if(files.exists(isSource)) files else Set()

		import Paths._
		withTemporaryDirectory { dir =>
			val extractedSources = (Set[File]() /: sourceJars) { (extracted, sourceJar)=> extracted ++ keepIfSource(unzip(sourceJar, dir)) }
			val (sourceFiles, resources) = extractedSources.partition(isSource)
			withTemporaryDirectory { outputDirectory =>
				val xsbtiJars = manager.files(xsbtiID)(IfMissing.Fail)
				manager.log.info("'" + id + "' not yet compiled for Scala " + compiler.scalaInstance.actualVersion + ". Compiling...")
				val start = System.currentTimeMillis
				try
				{
					compiler(Set() ++ sourceFiles, Set() ++ xsbtiJars ++ sourceJars, outputDirectory, "-nowarn" :: Nil)
					manager.log.info("  Compilation completed in " + (System.currentTimeMillis - start) / 1000.0 + " s")
				}
				catch { case e: xsbti.CompileFailed => throw new CompileFailed(e.arguments, "Error compiling sbt component '" + id + "'") }
				copy(resources x (FileMapper.rebase(dir, outputDirectory)))
				zip((outputDirectory ***) x (PathMapper.relativeTo(outputDirectory)), targetJar)
			}
		}
	}
	private def isSourceName(name: String): Boolean = name.endsWith(".scala") || name.endsWith(".java")
}