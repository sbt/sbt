/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt
package compiler

import java.io.File

/**
  * A basic interface to the compiler.  It is called in the same virtual machine, but no dependency analysis is done.  This
  * is used, for example, to compile the interface/plugin code.
  * If `explicitClasspath` is true, the bootclasspath and classpath are not augmented.  If it is false,
  * the scala-library.jar from `scalaInstance` is put on bootclasspath and the scala-compiler jar goes on the classpath.
  */
class RawCompiler(val scalaInstance: xsbti.compile.ScalaInstance, cp: ClasspathOptions, log: Logger) {
  def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String]): Unit = {
    // reflection is required for binary compatibility
    // The following import ensures there is a compile error if the identifiers change,
    //   but should not be otherwise directly referenced
    import scala.tools.nsc.Main.{ process => _ }

    val arguments = compilerArguments(sources, classpath, Some(outputDirectory), options)
    log.debug(
      "Plain interface to Scala compiler " + scalaInstance.actualVersion + "  with arguments: " + arguments
        .mkString("\n\t", "\n\t", "")
    )
    val args = arguments.toArray
    val reporter =
      if (ScalaInstance.isDotty(scalaInstance.version)) {
        val mainClass = Class.forName("dotty.tools.dotc.Main", true, scalaInstance.loader)
        val process = mainClass.getMethod("process", classOf[Array[String]])
        process.invoke(null, args)
      } else {
        val mainClass = Class.forName("scala.tools.nsc.Main", true, scalaInstance.loader)
        val process = mainClass.getMethod("process", classOf[Array[String]])
        process.invoke(null, arguments.toArray)
        mainClass.getMethod("reporter").invoke(null)
      }
    checkForFailure(reporter, arguments.toArray)
  }
  def compilerArguments = new CompilerArguments(scalaInstance, cp)
  protected def checkForFailure(reporter: AnyRef, args: Array[String]): Unit = {
    val failed = reporter.getClass.getMethod("hasErrors").invoke(reporter).asInstanceOf[Boolean]
    if (failed) throw new CompileFailed(args, "Plain compile failed", Array())
  }
  @deprecated("Use `checkForFailure(AnyRef, Array[String])`", "0.13.10")
  protected def checkForFailure(mainClass: Class[_], args: Array[String]): Unit = {
    val reporter = mainClass.getMethod("reporter").invoke(null)
    checkForFailure(reporter, args)
  }
}
class CompileFailed(val arguments: Array[String],
                    override val toString: String,
                    val problems: Array[xsbti.Problem])
    extends xsbti.CompileFailed
    with FeedbackProvidedException
