/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah, Seth Tisue
 */
package sbt
package compile

	import java.io.File

trait JavaCompiler
{
	def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger): Unit
}
object JavaCompiler
{
	def construct(f: (Seq[String], Logger) => Int, cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaCompiler =
		new JavaCompiler {
			def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger) {
				val augmentedClasspath = if(cp.autoBoot) classpath ++ Seq(scalaInstance.libraryJar) else classpath
				val javaCp = new ClasspathOptions(false, cp.compiler, false)
				val arguments = (new CompilerArguments(scalaInstance, javaCp))(sources, augmentedClasspath, outputDirectory, options)
				log.debug("running javac with arguments:\n\t" + arguments.mkString("\n\t"))
				val code: Int = f(arguments, log)
				log.debug("javac returned exit code: " + code)
				if( code != 0 ) throw new CompileFailed(arguments.toArray, "javac returned nonzero exit code")
			}
		}
	def directOrFork(cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaCompiler = construct(directOrForkJavac, cp, scalaInstance)
	def direct(cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaCompiler = construct(directJavac, cp, scalaInstance)
	def fork(cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaCompiler = construct(forkJavac, cp, scalaInstance)
	
	val directOrForkJavac = (arguments: Seq[String], log: Logger) => 
		try { directJavac(arguments, log) }
		catch { case e: ClassNotFoundException => 
			log.debug("com.sun.tools.javac.Main not found; forking javac instead")
			forkJavac(arguments, log)
		}

	val forkJavac = (arguments: Seq[String], log: Logger) =>
	{
		def externalJavac(argFile: File) = Process("javac", ("@" + normalizeSlash(argFile.getAbsolutePath)) :: Nil) ! log
		withArgumentFile(arguments)(externalJavac)
	}
	val directJavac = (arguments: Seq[String], log: Logger) =>
	{
		val writer = new java.io.PrintWriter(new LoggerWriter(log, Level.Error))
		val argsArray = arguments.toArray
		val javac = Class.forName("com.sun.tools.javac.Main")
		log.debug("Calling javac directly.")
		javac.getDeclaredMethod("compile", classOf[Array[String]], classOf[java.io.PrintWriter])
		.invoke(null, argsArray, writer)
		.asInstanceOf[java.lang.Integer]
		.intValue
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