package xsbt

	import java.io.File

/** A basic interface to the compiler.  It is called in the same virtual machine, but no dependency analysis is done.  This
* is used, for example, to compile the interface/plugin code.*/
class RawCompiler(val scalaInstance: ScalaInstance, log: CompileLogger)
{
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String]): Unit =
		apply(sources, classpath, outputDirectory, options, false)
	def apply(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], compilerOnClasspath: Boolean)
	{
		// reflection is required for binary compatibility
			// The following imports ensure there is a compile error if the identifiers change,
			//   but should not be otherwise directly referenced
			import scala.tools.nsc.Main
			import scala.tools.nsc.Properties

		val arguments = (new CompilerArguments(scalaInstance))(sources, classpath, outputDirectory, options, compilerOnClasspath)
		log.debug("Vanilla interface to Scala compiler " + scalaInstance.actualVersion + "  with arguments: " + arguments.mkString("\n\t", "\n\t", ""))
		val mainClass = Class.forName("scala.tools.nsc.Main", true, scalaInstance.loader)
		val process = mainClass.getMethod("process", classOf[Array[String]])
		process.invoke(null, toJavaArray(arguments))
		checkForFailure(mainClass, arguments.toArray)
	}
	protected def checkForFailure(mainClass: Class[_], args: Array[String])
	{
		val reporter = mainClass.getMethod("reporter").invoke(null)
		val failed = reporter.asInstanceOf[{ def hasErrors: Boolean }].hasErrors
		if(failed) throw new xsbti.CompileFailed { val arguments = args; override def toString = "Vanilla compile failed" }
	}
	protected def toJavaArray(arguments: Seq[String]): Array[String] =
	{
		val realArray: Array[String] = arguments.toArray
		assert(realArray.getClass eq classOf[Array[String]])
		realArray
	}
}