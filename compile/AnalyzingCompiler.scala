package xsbt

	import xsbti.{AnalysisCallback, Logger => xLogger}
	import java.io.File
	import java.net.URLClassLoader

/** Interface to the Scala compiler that uses the dependency analysis plugin.  This class uses the Scala library and compiler
*  obtained through the 'scalaLoader' class loader.  This class requires a ComponentManager in order to obtain the
*  interface code to scalac and the analysis plugin.  Because these call Scala code for a different Scala version, they must
* be compiled for the version of Scala being used.  It is essential that the provided 'scalaVersion' be a 1:1 mapping to the
* actual version of Scala being used for compilation (-SNAPSHOT is not acceptable).  Otherwise, binary compatibility
* issues will ensue!*/
class AnalyzingCompiler(scalaInstance: ScalaInstance, manager: ComponentManager) extends NotNull
{
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], callback: AnalysisCallback, maximumErrors: Int, log: CompileLogger): Unit =
		apply(sources, classpath, outputDirectory, options, false, callback, maximumErrors, log)
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], compilerOnClasspath: Boolean,
		 callback: AnalysisCallback, maximumErrors: Int, log: CompileLogger)
	{
		val arguments = (new CompilerArguments(scalaInstance))(sources, classpath, outputDirectory, options, compilerOnClasspath)
		// this is the instance used to compile the analysis
		val componentCompiler = new ComponentCompiler(new RawCompiler(scalaInstance, log), manager)
		log.debug("Getting " + ComponentCompiler.compilerInterfaceID + " from component compiler for Scala " + scalaInstance.version)
		val interfaceJar = componentCompiler(ComponentCompiler.compilerInterfaceID)
		val dual = createDualLoader(scalaInstance.loader, getClass.getClassLoader) // this goes to scalaLoader for scala classes and sbtLoader for xsbti classes
		val interfaceLoader = new URLClassLoader(Array(interfaceJar.toURI.toURL), dual)
		val interface = Class.forName("xsbt.CompilerInterface", true, interfaceLoader).newInstance.asInstanceOf[AnyRef]
		val runnable = interface.asInstanceOf[{ def run(args: Array[String], callback: AnalysisCallback, maximumErrors: Int, log: xLogger): Unit }]
			// these arguments are safe to pass across the ClassLoader boundary because the types are defined in Java
		//  so they will be binary compatible across all versions of Scala
		runnable.run(arguments.toArray, callback, maximumErrors, log)
	}
	protected def createDualLoader(scalaLoader: ClassLoader, sbtLoader: ClassLoader): ClassLoader =
	{
		val xsbtiFilter = (name: String) => name.startsWith("xsbti.")
		val notXsbtiFilter = (name: String) => !xsbtiFilter(name)
		new DualLoader(scalaLoader, notXsbtiFilter, x => true, sbtLoader, xsbtiFilter, x => false)
	}
	override def toString = "Analyzing compiler (Scala " + scalaInstance.actualVersion + ")"
}
object AnalyzingCompiler
{
	def apply(scalaVersion: String, provider: xsbti.ScalaProvider, manager: ComponentManager): AnalyzingCompiler =
		new AnalyzingCompiler(ScalaInstance(scalaVersion, provider), manager)
}