/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah, Seth Tisue
 */
package sbt
package compiler

import java.io.{File, PrintWriter}

abstract class JavacContract(val name: String, val clazz: String) {
	def exec(args: Array[String], writer: PrintWriter): Int
}
trait JavaCompiler
{
	def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger) {
		compile(JavaCompiler.javac, sources, classpath, outputDirectory, options)(log)
	}
	def doc(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], maximumErrors: Int, log: Logger) {
		compile(JavaCompiler.javadoc, sources, classpath, outputDirectory, options)(log)
	}
	def compile(contract: JavacContract, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger): Unit
}
object JavaCompiler
{
	type Fork = (JavacContract, Seq[String], Logger) => Int

        private def pwClass          = classOf[PrintWriter]
        private def arrayStringClass = classOf[Array[String]]
        private def docletsClass     = "com.sun.tools.doclets.standard.Standard"

        // The real performance gain comes if you can skip the indirection.
        /* val javac = new JavacContract("javac", "com.sun.tools.javac.Main") {
                def exec(args: Array[String], writer: PrintWriter): Int =
                com.sun.tools.javac.Main.compile(args, writer)
        } */

	val javac = new JavacContract("javac", "com.sun.tools.javac.Main") {
                private val execMethod = Class.forName(clazz).getDeclaredMethod("compile", arrayStringClass, pwClass)
                def exec(args: Array[String], writer: PrintWriter) = {
                        execMethod.invoke(null, args, writer).asInstanceOf[java.lang.Integer].intValue
		}
	}
	val javadoc = new JavacContract("javadoc", "com.sun.tools.javadoc.Main") {
                private val execMethod   = Class.forName(clazz).getDeclaredMethod("execute", classOf[String], pwClass, pwClass, pwClass, classOf[String], arrayStringClass)

                def exec(args: Array[String], writer: PrintWriter) =
                        execMethod.invoke(null, name, writer, writer, writer, docletsClass, args).asInstanceOf[java.lang.Integer].intValue
	}

	def construct(f: Fork, cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaCompiler =
		new JavaCompiler {
			def compile(contract: JavacContract, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger) {
				val augmentedClasspath = if(cp.autoBoot) classpath ++ Seq(scalaInstance.libraryJar) else classpath
				val javaCp = ClasspathOptions.javac(cp.compiler)
				val arguments = (new CompilerArguments(scalaInstance, javaCp))(sources, augmentedClasspath, outputDirectory, options)
				log.debug("Calling " + contract.name.capitalize + " with arguments:\n\t" + arguments.mkString("\n\t"))
				val code: Int = f(contract, arguments, log)
				log.debug(contract.name + " returned exit code: " + code)
				if( code != 0 ) throw new CompileFailed(arguments.toArray, contract.name + " returned nonzero exit code")
			}
		}
	def directOrFork(cp: ClasspathOptions, scalaInstance: ScalaInstance)(implicit doFork: Fork): JavaCompiler =
		construct(directOrForkJavac, cp, scalaInstance)
		
	def direct(cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaCompiler =
		construct(directJavac, cp, scalaInstance)
	
	def fork(cp: ClasspathOptions, scalaInstance: ScalaInstance)(implicit doFork: Fork): JavaCompiler =
		construct(forkJavac, cp, scalaInstance)
	
	def directOrForkJavac(implicit doFork: Fork) = (contract: JavacContract, arguments: Seq[String], log: Logger) =>
		try { directJavac(contract, arguments, log) }
		catch { case e @ (_: ClassNotFoundException | _: NoSuchMethodException) =>
			log.debug(contract.clazz + " not found with appropriate method signature; forking " + contract.name + " instead")
			forkJavac(doFork)(contract, arguments, log)
		}

	/** `doFork` should be a function that forks javac with the provided arguments and sends output to the given Logger.*/
	def forkJavac(implicit doFork: Fork) = (contract: JavacContract, arguments: Seq[String], log: Logger) =>
	{
		val (jArgs, nonJArgs) = arguments.partition(_.startsWith("-J"))
		def externalJavac(argFile: File) = doFork(contract, jArgs :+ ("@" + normalizeSlash(argFile.getAbsolutePath)), log)
		withArgumentFile(nonJArgs)(externalJavac)
	}
	val directJavac = (contract: JavacContract, arguments: Seq[String], log: Logger) =>
	{
		val logger = new LoggerWriter(log)
		val writer = new PrintWriter(logger)
		val argsArray = arguments.toArray
		log.debug("Attempting to call " + contract.name + " directly...")

		var exitCode = -1
		try { exitCode = contract.exec(argsArray, writer) }
		finally { logger.flushLines( if(exitCode == 0) Level.Warn else Level.Error) }
		exitCode
	}
	def withArgumentFile[T](args: Seq[String])(f: File => T): T =
	{
		import IO.{Newline, withTemporaryDirectory, write}
		withTemporaryDirectory { tmp =>
			val argFile = new File(tmp, "argfile")
			write(argFile, args.map(escapeSpaces).mkString(Newline))
			f(argFile)
		}
	}
	// javac's argument file seems to allow naive space escaping with quotes.  escaping a quote with a backslash does not work
	def escapeSpaces(s: String): String = '\"' + normalizeSlash(s) + '\"'
	def normalizeSlash(s: String) = s.replace(File.separatorChar, '/')
}