package sbt.compiler.javac

import sbt.ClasspathOptions
import sbt.{ ClasspathOptions => _, _ }
import sbt.compiler._
import java.io.{ File, PrintWriter }

import javax.tools.{ Diagnostic, DiagnosticCollector, DiagnosticListener, JavaFileObject }
import xsbti.compile.ScalaInstance
import xsbti.compile._
import xsbti.{ Reporter, Severity }

/**
  * An interface to the toolchain of Java.
  *
  * Specifically, access to run javadoc + javac.
  */
sealed trait JavaTools {

  /** The raw interface of the java compiler for direct access. */
  def compiler: JavaTool

  /**
    * This will run a java compiler.
    *
    *
    * @param sources  The list of java source files to compile.
    * @param options  The set of options to pass to the java compiler (includes the classpath).
    * @param log      The logger to dump output into.
    * @param reporter The reporter for semantic error messages.
    * @return true if no errors, false otherwise.
    */
  def compile(sources: Seq[File], options: Seq[String])(implicit log: Logger, reporter: Reporter): Boolean

  /**
    * This will run a java compiler.
    *
    *
    * @param sources  The list of java source files to compile.
    * @param options  The set of options to pass to the java compiler (includes the classpath).
    * @param log      The logger to dump output into.
    * @param reporter The reporter for semantic error messages.
    * @return true if no errors, false otherwise.
    */
  def doc(sources: Seq[File], options: Seq[String])(implicit log: Logger, reporter: Reporter): Boolean
}

/**
  * An extension of the JavaTools trait that also includes interfaces specific to running
  * the java compiler inside of the incremental comppiler.
  */
sealed trait IncrementalCompilerJavaTools extends JavaTools {

  /** An instance of the java Compiler for use with incremental compilation. */
  def xsbtiCompiler: xsbti.compile.JavaCompiler
}

/** Factory methods for getting a java toolchain. */
object JavaTools {

  /** Create a new aggregate tool from existing tools. */
  def apply(c: JavaCompiler, docgen: Javadoc): JavaTools =
    new JavaTools {
      override def compiler = c
      def compile(sources: Seq[File], options: Seq[String])(implicit log: Logger,
                                                            reporter: Reporter): Boolean =
        c.run(sources, options)
      def doc(sources: Seq[File], options: Seq[String])(implicit log: Logger, reporter: Reporter): Boolean =
        docgen.run(sources, options)
    }

  /**
    * Constructs a new set of java toolchain for incremental compilation.
    *
    * @param instance
    *            The scalaInstance being used in this incremental compile.  Used if we need to append
    *            scala to the classpath (yeah.... the classpath doesn't already have it).
    * @param cpOptions
    *            Classpath options configured for this incremental compiler. Basically, should we append scala or not.
    * @param javaHome
    *            If this is defined, the location where we should look for javac when we run.
    * @return
    *            A new set of the Java toolchain that also includes and instance of xsbti.compile.JavaCompiler
    */
  def directOrFork(instance: xsbti.compile.ScalaInstance,
                   cpOptions: xsbti.compile.ClasspathOptions,
                   javaHome: Option[File]): IncrementalCompilerJavaTools = {
    val (compiler, doc) = javaHome match {
      case Some(_) => (JavaCompiler.fork(javaHome), Javadoc.fork(javaHome))
      case _ =>
        val c = JavaCompiler.local.getOrElse(JavaCompiler.fork(None))
        val d = Javadoc.local.getOrElse(Javadoc.fork())
        (c, d)
    }
    val delegate = apply(compiler, doc)
    new IncrementalCompilerJavaTools {
      val xsbtiCompiler = new JavaCompilerAdapter(delegate.compiler, instance, cpOptions)
      def compiler = delegate.compiler
      def compile(sources: Seq[File], options: Seq[String])(implicit log: Logger,
                                                            reporter: Reporter): Boolean =
        delegate.compile(sources, options)
      def doc(sources: Seq[File], options: Seq[String])(implicit log: Logger, reporter: Reporter): Boolean =
        delegate.doc(sources, options)
    }
  }
}

/**
  * An interface for on of the tools in the java tool chain.
  *
  * We assume the following is true of tools:
  * - The all take sources and options and log error messages
  * - They return success or failure.
  */
sealed trait JavaTool {

  /**
    * This will run a java compiler / or other like tool (e.g. javadoc).
    *
    *
    * @param sources  The list of java source files to compile.
    * @param options  The set of options to pass to the java compiler (includes the classpath).
    * @param log      The logger to dump output into.
    * @param reporter The reporter for semantic error messages.
    * @return true if no errors, false otherwise.
    */
  def run(sources: Seq[File], options: Seq[String])(implicit log: Logger, reporter: Reporter): Boolean
}

/** Interface we use to compile java code. This is mostly a tag over the raw JavaTool interface. */
trait JavaCompiler extends JavaTool {}

/** Factory methods for constructing a java compiler. */
object JavaCompiler {

  /** Returns a local compiler, if the current runtime supports it. */
  def local: Option[JavaCompiler] =
    for {
      compiler <- Option(javax.tools.ToolProvider.getSystemJavaCompiler)
    } yield new LocalJavaCompiler(compiler)

  /** Returns a local compiler that will fork javac when needed. */
  def fork(javaHome: Option[File] = None): JavaCompiler =
    new ForkedJavaCompiler(javaHome)

}

/** Interface we use to document java code. This is a tag over the raw JavaTool interface. */
trait Javadoc extends JavaTool {}

/** Factory methods for constructing a javadoc. */
object Javadoc {

  /** Returns a local compiler, if the current runtime supports it. */
  def local: Option[Javadoc] =
    // TODO - javax doc tool not supported in JDK6
    //Option(javax.tools.ToolProvider.getSystemDocumentationTool)
    if (LocalJava.hasLocalJavadoc) Some(new LocalJavadoc)
    else None

  /** Returns a local compiler that will fork javac when needed. */
  def fork(javaHome: Option[File] = None): Javadoc =
    new ForkedJavadoc(javaHome)

}
