/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah, Seth Tisue
 */
package sbt
package compiler

	import java.io.File

trait JavaCompiler
{
	def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger): Unit
}
object JavaCompiler
{
	type Fork = (Seq[String], Logger) => Int

	def construct(f: (Seq[String], Logger) => Int, cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaCompiler =
		new JavaCompiler {
			def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger) {
				val augmentedClasspath = if(cp.autoBoot) classpath ++ Seq(scalaInstance.libraryJar) else classpath
				val javaCp = ClasspathOptions.javac(cp.compiler)
				val arguments = (new CompilerArguments(scalaInstance, javaCp))(sources, augmentedClasspath, outputDirectory, options)
				log.debug("running javac with arguments:\n\t" + arguments.mkString("\n\t"))
				val code: Int = f(arguments, log)
				log.debug("javac returned exit code: " + code)
				if( code != 0 ) throw new CompileFailed(arguments.toArray, "javac returned nonzero exit code")
			}
		}
	def directOrFork(cp: ClasspathOptions, scalaInstance: ScalaInstance)(implicit doFork: Fork): JavaCompiler =
		construct(directOrForkJavac, cp, scalaInstance)
		
	def direct(cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaCompiler =
		construct(directJavac, cp, scalaInstance)
	
	def fork(cp: ClasspathOptions, scalaInstance: ScalaInstance)(implicit doFork: Fork): JavaCompiler =
		construct(forkJavac, cp, scalaInstance)
	
	def directOrForkJavac(implicit doFork: Fork) = (arguments: Seq[String], log: Logger) => 
		try { directJavac(arguments, log) }
		catch { case e: ClassNotFoundException => 
			log.debug("com.sun.tools.javac.Main not found; forking javac instead")
			forkJavac(doFork)(arguments, log)
		}

	/** `doFork` should be a function that forks javac with the provided arguments and sends output to the given Logger.*/
	def forkJavac(implicit doFork: Fork) = (arguments: Seq[String], log: Logger) =>
	{
		val (jArgs, nonJArgs) = arguments.partition(_.startsWith("-J"))
		def externalJavac(argFile: File) = doFork(jArgs :+ ("@" + normalizeSlash(argFile.getAbsolutePath)), log)
		withArgumentFile(nonJArgs)(externalJavac)
	}
	val directJavac = (arguments: Seq[String], log: Logger) =>
	{
		val logger = new LoggerWriter(log)
		val writer = new java.io.PrintWriter(logger)
		val argsArray = arguments.toArray
		val javac = Class.forName("com.sun.tools.javac.Main")
		log.debug("Calling javac directly.")
		val compileMethod = javac.getDeclaredMethod("compile", classOf[Array[String]], classOf[java.io.PrintWriter])

		var exitCode = -1
		try { exitCode = compileMethod.invoke(null, argsArray, writer).asInstanceOf[java.lang.Integer].intValue }
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