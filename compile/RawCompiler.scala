package xsbt

	import java.io.File

/** A basic interface to the compiler.  It is called in the same virtual machine, but no dependency analysis is done.  This
* is used, for example, to compile the interface/plugin code.
* If `explicitClasspath` is true, the bootclasspath and classpath are not augmented.  If it is false,
* the scala-library.jar from `scalaInstance` is put on bootclasspath and the scala-compiler jar goes on the classpath.*/
class RawCompiler(val scalaInstance: ScalaInstance, autoBootClasspath: Boolean, compilerOnClasspath: Boolean, log: CompileLogger)
{
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String])
	{
		// reflection is required for binary compatibility
			// The following import ensures there is a compile error if the identifiers change,
			//   but should not be otherwise directly referenced
			import scala.tools.nsc.Main.{process => _}

		val arguments = compilerArguments(sources, classpath, outputDirectory, options)
		log.debug("Plain interface to Scala compiler " + scalaInstance.actualVersion + "  with arguments: " + arguments.mkString("\n\t", "\n\t", ""))
		val mainClass = Class.forName("scala.tools.nsc.Main", true, scalaInstance.loader)
		val process = mainClass.getMethod("process", classOf[Array[String]])
		process.invoke(null, toJavaArray(arguments))
		checkForFailure(mainClass, arguments.toArray)
	}
	def compilerArguments = new CompilerArguments(scalaInstance, autoBootClasspath, compilerOnClasspath)
	protected def checkForFailure(mainClass: Class[_], args: Array[String])
	{
		val reporter = mainClass.getMethod("reporter").invoke(null)
		val failed = reporter.getClass.getMethod("hasErrors").invoke(reporter).asInstanceOf[Boolean]
		if(failed) throw new CompileFailed(args, "Plain compile failed")
	}
	protected def toJavaArray(arguments: Seq[String]): Array[String] =
	{
		val realArray: Array[String] = arguments.toArray
		assert(realArray.getClass eq classOf[Array[String]])
		realArray
	}
}
class CompileFailed(val arguments: Array[String], override val toString: String) extends xsbti.CompileFailed
{
	override def fillInStackTrace = this
}