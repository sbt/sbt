package xsbt

import java.io.File
import sbt.{ComponentManager, IfMissing, InvalidComponent}

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
	import sbt.IO.{copy, createDirectory, zip, jars, unzip, withTemporaryDirectory}
	def apply(id: String): File =
		try { getPrecompiled(id) }
		catch { case _: InvalidComponent => getLocallyCompiled(id) }

	/** Gets the precompiled (distributed with sbt) component with the given 'id'
	* If the component has not been precompiled, this throws InvalidComponent. */
	def getPrecompiled(id: String): File = manager.file( binaryID(id, false) )(IfMissing.Fail)
	/** Get the locally compiled component with the given 'id' or compiles it if it has not been compiled yet.
	* If the component does not exist, this throws InvalidComponent. */
	def getLocallyCompiled(id: String): File =
	{
		val binID = binaryID(id, true)
		manager.file(binID)( new IfMissing.Define(true, compileAndInstall(id, binID)) )
	}
	def clearCache(id: String): Unit = manager.clearCache(binaryID(id, true))
	protected def binaryID(id: String, withJavaVersion: Boolean) =
	{
		val base = id + binSeparator + compiler.scalaInstance.actualVersion
		if(withJavaVersion) base + "__" + javaVersion else base
	}
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

		withTemporaryDirectory { dir =>
			val extractedSources = (Set[File]() /: sourceJars) { (extracted, sourceJar)=> extracted ++ keepIfSource(unzip(sourceJar, dir)) }
			val (sourceFiles, resources) = extractedSources.partition(isSource)
			withTemporaryDirectory { outputDirectory =>
				val xsbtiJars = manager.files(xsbtiID)(IfMissing.Fail)
				manager.log.info("'" + id + "' not yet compiled for Scala " + compiler.scalaInstance.actualVersion + ". Compiling...")
				val start = System.currentTimeMillis
				try
				{
					compiler(sourceFiles.toSeq, xsbtiJars.toSeq ++ sourceJars, outputDirectory, "-nowarn" :: Nil)
					manager.log.info("  Compilation completed in " + (System.currentTimeMillis - start) / 1000.0 + " s")
				}
				catch { case e: xsbti.CompileFailed => throw new CompileFailed(e.arguments, "Error compiling sbt component '" + id + "'") }
				import sbt.Path._
				copy(resources x rebase(dir, outputDirectory))
				zip((outputDirectory ***) x_! relativeTo(outputDirectory), targetJar)
			}
		}
	}
	private def isSourceName(name: String): Boolean = name.endsWith(".scala") || name.endsWith(".java")
}