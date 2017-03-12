package sbt.compiler.javac

import java.io.File

import sbt.compiler.{ CompileFailed, CompilerArguments }
import sbt.{ ClasspathOptions, Logger, LoggerReporter }
import xsbti.Reporter
import xsbti.compile.{ MultipleOutput, Output, SingleOutput }

/**
  * This class adapts the new java compiler with the classpath/argument option hackery needed to handle scala.
  *
  * The xsbti.Compiler interface is used by the IncrementalCompiler classes, so this lets us adapt a more generic
  * wrapper around running Javac (forked or direct) into the interfaces used by incremental compiler.
  *
  */
class JavaCompilerAdapter(delegate: JavaTool,
                          scalaInstance: xsbti.compile.ScalaInstance,
                          cpOptions: xsbti.compile.ClasspathOptions)
    extends xsbti.compile.JavaCompiler {
  override final def compile(sources: Array[File],
                             classpath: Array[File],
                             output: Output,
                             options: Array[String],
                             log: xsbti.Logger): Unit = {
    // TODO - 5 max errors ok?  We're not expecting this code path to be called, ever.  This is only for clients who try to use the xsbti.compile.JavaCompiler interface
    // outside of the incremental compiler, for some reason.
    val reporter = new LoggerReporter(5, log)
    compileWithReporter(sources, classpath, output, options, reporter, log)
  }
  override final def compileWithReporter(sources: Array[File],
                                         classpath: Array[File],
                                         output: Output,
                                         options: Array[String],
                                         reporter: Reporter,
                                         log: xsbti.Logger): Unit = {
    val target = output match {
      case so: SingleOutput => so.outputDirectory
      case mo: MultipleOutput =>
        throw new RuntimeException("Javac doesn't support multiple output directories")
    }
    val args = commandArguments(Seq(), classpath, target, options, log)
    // We sort the sources for deterministic results.
    val success = delegate.run(sources.sortBy(_.getAbsolutePath), args)(log, reporter)
    if (!success) {
      // TODO - Will the reporter have problems from Scalac?  It appears like it does not, only from the most recent run.
      // This is because the incremental compiler will not run javac if scalac fails.
      throw new CompileFailed(args.toArray, "javac returned nonzero exit code", reporter.problems())
    }
  }
  private[this] def commandArguments(sources: Seq[File],
                                     classpath: Seq[File],
                                     outputDirectory: File,
                                     options: Seq[String],
                                     log: Logger): Seq[String] = {
    val augmentedClasspath = if (cpOptions.autoBoot) classpath ++ Seq(scalaInstance.libraryJar) else classpath
    val javaCp = ClasspathOptions.javac(cpOptions.compiler)
    (new CompilerArguments(scalaInstance, javaCp))(sources, augmentedClasspath, Some(outputDirectory), options)
  }
}
