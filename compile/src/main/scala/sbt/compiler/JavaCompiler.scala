/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah, Seth Tisue
 */
package sbt
package compiler

import java.io.{ File, PrintWriter }

import xsbti.{ Severity, Reporter }
import xsbti.compile.Output

@deprecated("Please use the new set of compilers in sbt.compilers.javac", "0.13.8")
abstract class JavacContract(val name: String, val clazz: String) {
  def exec(args: Array[String], writer: PrintWriter): Int
}
/** An interface we use to call the Java compiler. */
@deprecated("Please use the new set of compilers in sbt.compilers.javac", "0.13.8")
trait JavaCompiler extends xsbti.compile.JavaCompiler {
  /**
   * Runs the java compiler
   *
   * @param sources  The source files to compile
   * @param classpath The classpath for the compiler
   * @param outputDirectory The output directory for class files
   * @param options The arguments to pass into Javac
   * @param log  A log in which we write all the output from Javac.
   */
  def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger): Unit

  def compile(sources: Array[File], classpath: Array[File], output: xsbti.compile.Output, options: Array[String], log: xsbti.Logger): Unit = {
    val outputDirectory = output match {
      case single: xsbti.compile.SingleOutput => single.outputDirectory
      case _                                  => throw new RuntimeException("Javac doesn't support multiple output directories")
    }
    apply(sources, classpath, outputDirectory, options)(log)
  }

  // TODO - Fix this so that the reporter is actually used.
  def compileWithReporter(sources: Array[File], classpath: Array[File], output: Output, options: Array[String], reporter: Reporter, log: xsbti.Logger): Unit = {
    compile(sources, classpath, output, options, log)
  }

  def onArgs(f: Seq[String] => Unit): JavaCompiler
}
@deprecated("Please use the new set of compilers in sbt.compilers.javac", "0.13.8")
trait Javadoc {
  def doc(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], maximumErrors: Int, log: Logger)

  def onArgs(f: Seq[String] => Unit): Javadoc
}
@deprecated("Please use the new set of compilers in sbt.compilers.javac", "0.13.8")
trait JavaTool extends Javadoc with JavaCompiler {
  def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger) =
    compile(JavaCompiler.javac, sources, classpath, outputDirectory, options)(log)

  def doc(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], maximumErrors: Int, log: Logger) =
    compile(JavaCompiler.javadoc, sources, classpath, outputDirectory, options)(log)

  def compile(contract: JavacContract, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger): Unit

  def onArgs(f: Seq[String] => Unit): JavaTool
}
@deprecated("Please use the new set of compilers in sbt.compilers.javac", "0.13.8")
object JavaCompiler {
  @deprecated("Please use the new set of compilers in sbt.compilers.javac", "0.13.8")
  type Fork = (JavacContract, Seq[String], Logger) => Int

  val javac = new JavacContract("javac", "com.sun.tools.javac.Main") {
    def exec(args: Array[String], writer: PrintWriter) = {
      val m = Class.forName(clazz).getDeclaredMethod("compile", classOf[Array[String]], classOf[PrintWriter])
      m.invoke(null, args, writer).asInstanceOf[java.lang.Integer].intValue
    }
  }
  val javadoc = new JavacContract("javadoc", "com.sun.tools.javadoc.Main") {
    def exec(args: Array[String], writer: PrintWriter) = {
      val m = Class.forName(clazz).getDeclaredMethod("execute", classOf[String], classOf[PrintWriter], classOf[PrintWriter], classOf[PrintWriter], classOf[String], classOf[Array[String]])
      m.invoke(null, name, writer, writer, writer, "com.sun.tools.doclets.standard.Standard", args).asInstanceOf[java.lang.Integer].intValue
    }
  }

  def construct(f: Fork, cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaTool = new JavaTool0(f, cp, scalaInstance, _ => ())

  /** The actual implementation of a JavaTool (javadoc + javac). */
  private[this] class JavaTool0(f: Fork, cp: ClasspathOptions, scalaInstance: ScalaInstance, onArgsF: Seq[String] => Unit) extends JavaTool {
    def onArgs(g: Seq[String] => Unit): JavaTool = new JavaTool0(f, cp, scalaInstance, g)
    def commandArguments(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger): Seq[String] =
      {
        val augmentedClasspath = if (cp.autoBoot) classpath ++ Seq(scalaInstance.libraryJar) else classpath
        val javaCp = ClasspathOptions.javac(cp.compiler)
        (new CompilerArguments(scalaInstance, javaCp))(sources, augmentedClasspath, Some(outputDirectory), options)
      }
    def compile(contract: JavacContract, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger): Unit = {
      val arguments = commandArguments(sources, classpath, outputDirectory, options, log)
      onArgsF(arguments)
      val code: Int = f(contract, arguments, log)
      log.debug(contract.name + " returned exit code: " + code)
      if (code != 0) throw new CompileFailed(arguments.toArray, contract.name + " returned nonzero exit code", Array())
    }
  }
  def directOrFork(cp: ClasspathOptions, scalaInstance: ScalaInstance)(implicit doFork: Fork): JavaTool =
    construct(directOrForkJavac, cp, scalaInstance)

  def direct(cp: ClasspathOptions, scalaInstance: ScalaInstance): JavaTool =
    construct(directJavac, cp, scalaInstance)

  def fork(cp: ClasspathOptions, scalaInstance: ScalaInstance)(implicit doFork: Fork): JavaTool =
    construct(forkJavac, cp, scalaInstance)

  def directOrForkJavac(implicit doFork: Fork) = (contract: JavacContract, arguments: Seq[String], log: Logger) =>
    try { directJavac(contract, arguments, log) }
    catch {
      case e @ (_: ClassNotFoundException | _: NoSuchMethodException) =>
        log.debug(contract.clazz + " not found with appropriate method signature; forking " + contract.name + " instead")
        forkJavac(doFork)(contract, arguments, log)
    }

  /** `doFork` should be a function that forks javac with the provided arguments and sends output to the given Logger.*/
  def forkJavac(implicit doFork: Fork) = (contract: JavacContract, arguments: Seq[String], log: Logger) =>
    {
      val (jArgs, nonJArgs) = arguments.partition(_.startsWith("-J"))
      def externalJavac(argFile: File) =
        doFork(contract, jArgs :+ ("@" + normalizeSlash(argFile.getAbsolutePath)), log)
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
      finally { logger.flushLines(if (exitCode == 0) Level.Warn else Level.Error) }
      exitCode
    }

  /**
   * Helper method to create an argument file that we pass to Javac.  Gets over the windows
   * command line length limitation.
   * @param args The string arguments to pass to Javac.
   * @param f  A function which is passed the arg file.
   * @tparam T The return type.
   * @return  The result of using the argument file.
   */
  def withArgumentFile[T](args: Seq[String])(f: File => T): T =
    {
      import IO.{ Newline, withTemporaryDirectory, write }
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
