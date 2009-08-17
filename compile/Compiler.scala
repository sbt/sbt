package xsbt

import xsbti.{AnalysisCallback, Logger}
import java.io.File
import java.net.URLClassLoader

/** A component manager provides access to the pieces of xsbt that are distributed as components.
* There are two types of components.  The first type is compiled subproject jars with their dependencies.
* The second type is a subproject distributed as a source jar so that it can be compiled against a specific
* version of Scala.
*
* The component manager provides services to install and retrieve components to the local repository.
* This is used for source jars so that the compilation need not be repeated for other projects on the same
* machine.
*/
trait ComponentManager extends NotNull
{
	def directory(id: String): File =
	{
		error("TODO: implement")
	}
	def jars(id: String): Iterable[File] =
	{
		val dir = directory(id)
		if(dir.isDirectory)
			FileUtilities.jars(dir)
		else
			Nil
	}
}


/** Interface to the Scala compiler.  This class uses the Scala library and compiler obtained through the 'scalaLoader' class
* loader.  This class requires a ComponentManager in order to obtain the interface code to scalac and the analysis plugin.  Because
* these call Scala code for a different Scala version, they must be compiled for the version of Scala being used.
* It is essential that the provided 'scalaVersion' be a 1:1 mapping to the actual version of Scala being used for compilation
* (-SNAPSHOT is bad).  Otherwise, binary compatibility issues will ensue!*/
class Compiler(scalaLoader: ClassLoader, val scalaVersion: String, private[xsbt] val manager: ComponentManager)
{
	// this is the instance used to compile the analysis
	lazy val componentCompiler = new ComponentCompiler(this)
	/** A basic interface to the compiler.  It is called in the same virtual machine, but no dependency analysis is done.  This
	* is used, for example, to compile the interface/plugin code.*/
	object raw
	{
		def apply(arguments: Seq[String])
		{
			// reflection is required for binary compatibility
			 // The following inputs ensure there is a compile error if the class names change, but they should not actually be used
			import scala.tools.nsc.{CompilerCommand, FatalError, Global, Settings, reporters, util}
			val mainClass = Class.forName("scala.tools.nsc.Main", true, scalaLoader)
			val main = mainClass.asInstanceOf[{def process(args: Array[String]): Unit }]
			main.process(arguments.toArray)
		}
	}
	/** Interface to the compiler that uses the dependency analysis plugin.*/
	object analysis
	{
		/** The compiled plugin jar.  This will be passed to scalac as a compiler plugin.*/
		private lazy val analyzerJar =
			componentCompiler("analyzerPlugin").toList match
			{
				case x :: Nil => x
				case Nil => error("Analyzer plugin component not found")
				case xs => error("Analyzer plugin component must be a single jar (was: " + xs.mkString(", ") + ")")
			}
		/** The compiled interface jar.  This is used to configure and call the compiler.  It redirects logging and sets up
		* the dependency analysis plugin.*/
		private lazy val interfaceJars = componentCompiler("compilerInterface")
		def apply(arguments: Seq[String], callback: AnalysisCallback, maximumErrors: Int, log: Logger)
		{
			val argsWithPlugin = ("-Xplugin:" + analyzerJar.getAbsolutePath) :: arguments.toList
			val interfaceLoader = new URLClassLoader(interfaceJars.toSeq.map(_.toURI.toURL).toArray, scalaLoader)
			val interface = Class.forName("xsbt.CompilerInterface", true, interfaceLoader).newInstance
			val runnable = interface.asInstanceOf[{ def run(args: Array[String], callback: AnalysisCallback, maximumErrors: Int, log: Logger): Unit }]
			runnable.run(argsWithPlugin.toArray, callback, maximumErrors, log) // safe to pass across the ClassLoader boundary because the types are defined in Java
		}
		def forceInitialization() {interfaceJars; analyzerJar}
	}
}
class ComponentCompiler(compiler: Compiler)
{
	import compiler.{manager, scalaVersion}
	
	val xsbtiID = "xsbti"
	lazy val xsbtiJars =
	{
		val js = manager.jars(xsbtiID)
		if(js.isEmpty)
			error("Could not find required xsbti component")
		else
			js
	}
	
	import FileUtilities.{copy, createDirectory, zip, jars, unzip, withTemporaryDirectory}
	def apply(id: String): Iterable[File] =
	{
		val binID = id + "-bin_" + scalaVersion
		val binaryDirectory = manager.directory(binID)
		if(binaryDirectory.isDirectory)
			jars(binaryDirectory)
		else
		{
			createDirectory(binaryDirectory)
			val srcID = id + "-src"
			val srcDirectory = manager.directory(srcID)
			if(srcDirectory.isDirectory)
			{
				val targetJar = new File(binaryDirectory, id + ".jar")
				compileSources(srcDirectory, compiler, targetJar, id)
				Seq(targetJar)
			}
			else
				notFound(id)
		}
	}
	private def notFound(id: String) = error("Couldn't find xsbt source component " + id + " for Scala " + scalaVersion)
	private def compileSources(srcDirectory: File, compiler: Compiler, targetJar: File, id: String)
	{
		val sources = jars(srcDirectory)
		if(sources.isEmpty)
			notFound(id)
		else
		{
			withTemporaryDirectory { dir =>
				val extractedSources = (Set[File]() /: sources) { (extracted, sourceJar)=> extracted ++ unzip(sourceJar, dir) }
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
}