package xsbt

import xsbti.{AnalysisCallback, Logger => xLogger}
import java.io.File
import java.net.URLClassLoader

/** Interface to the Scala compiler.  This class uses the Scala library and compiler obtained through the 'scalaLoader' class
* loader.  This class requires a ComponentManager in order to obtain the interface code to scalac and the analysis plugin.  Because
* these call Scala code for a different Scala version, they must be compiled for the version of Scala being used.
* It is essential that the provided 'scalaVersion' be a 1:1 mapping to the actual version of Scala being used for compilation
* (-SNAPSHOT is not acceptable).  Otherwise, binary compatibility issues will ensue!*/

/** A basic interface to the compiler.  It is called in the same virtual machine, but no dependency analysis is done.  This
* is used, for example, to compile the interface/plugin code.*/
class RawCompiler(scalaLoader: ClassLoader, scalaLibDirectory: File, log: CompileLogger)
{
	lazy val scalaVersion = Class.forName("scala.tools.nsc.Properties", true, scalaLoader).getMethod("versionString").invoke(null)
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String]): Unit =
		apply(sources, classpath, outputDirectory, options, false)
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], compilerOnClasspath: Boolean)
	{
		// reflection is required for binary compatibility
			// The following imports ensure there is a compile error if the identifiers change,
			//   but should not be otherwise directly referenced
		import scala.tools.nsc.Main
		import scala.tools.nsc.Properties

		val arguments = CompilerArguments(scalaLibDirectory)(sources, classpath, outputDirectory, options, compilerOnClasspath)
		log.debug("Vanilla interface to Scala compiler " + scalaVersion + "  with arguments: " + arguments.mkString("\n\t", "\n\t", ""))
		val mainClass = Class.forName("scala.tools.nsc.Main", true, scalaLoader)
		val process = mainClass.getMethod("process", classOf[Array[String]])
		val realArray: Array[String] = arguments.toArray
		assert(realArray.getClass eq classOf[Array[String]])
		process.invoke(null, realArray)
		checkForFailure(mainClass, arguments.toArray)
	}
	protected def checkForFailure(mainClass: Class[_], args: Array[String])
	{
		val reporter = mainClass.getMethod("reporter").invoke(null)
		val failed = reporter.asInstanceOf[{ def hasErrors: Boolean }].hasErrors
		if(failed) throw new xsbti.CompileFailed { val arguments = args; override def toString = "Vanilla compile failed" }
	}
}
/** Interface to the compiler that uses the dependency analysis plugin.*/
class AnalyzeCompiler(scalaVersion: String, scalaLoader: ClassLoader, scalaLibDirectory: File, manager: ComponentManager) extends NotNull
{
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], callback: AnalysisCallback, maximumErrors: Int, log: CompileLogger): Unit =
		apply(sources, classpath, outputDirectory, options, false, callback, maximumErrors, log)
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], compilerOnClasspath: Boolean,
		 callback: AnalysisCallback, maximumErrors: Int, log: CompileLogger)
	{
		val arguments = CompilerArguments(scalaLibDirectory)(sources, classpath, outputDirectory, options, compilerOnClasspath)
		// this is the instance used to compile the analysis
		val componentCompiler = new ComponentCompiler(scalaVersion, new RawCompiler(scalaLoader, scalaLibDirectory, log), manager)
		log.debug("Getting " + ComponentCompiler.compilerInterfaceID + " from component compiler for Scala " + scalaVersion + " (loader=" + scalaLoader + ")")
		val interfaceJar = componentCompiler(ComponentCompiler.compilerInterfaceID)
		val dual = createDualLoader(scalaLoader, getClass.getClassLoader) // this goes to scalaLoader for scala classes and sbtLoader for xsbti classes
		val interfaceLoader = new URLClassLoader(Array(interfaceJar.toURI.toURL), dual)
		val interface = Class.forName("xsbt.CompilerInterface", true, interfaceLoader).newInstance.asInstanceOf[AnyRef]
		val runnable = interface.asInstanceOf[{ def run(args: Array[String], callback: AnalysisCallback, maximumErrors: Int, log: xLogger): Unit }]
			// these arguments are safe to pass across the ClassLoader boundary because the types are defined in Java
		//  so they will be binary compatible across all versions of Scala
		runnable.run(arguments.toArray, callback, maximumErrors, log)
	}
	private def createDualLoader(scalaLoader: ClassLoader, sbtLoader: ClassLoader): ClassLoader =
	{
		val xsbtiFilter = (name: String) => name.startsWith("xsbti.")
		val notXsbtiFilter = (name: String) => !xsbtiFilter(name)
		new DualLoader(scalaLoader, notXsbtiFilter, x => true, sbtLoader, xsbtiFilter, x => false)
	}
	override def toString = "Analyzing compiler (Scala " + scalaVersion + ")"
}
object AnalyzeCompiler
{
	def apply(scalaVersion: String, provider: xsbti.ScalaProvider, manager: ComponentManager): AnalyzeCompiler =
		new AnalyzeCompiler(scalaVersion, provider.getScalaLoader(scalaVersion), provider.getScalaHome(scalaVersion), manager)
}