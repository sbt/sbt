/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah, Seth Tisue
 */
package sbt

import java.io.File
import xsbt.{AnalyzingCompiler, CompileFailed, CompilerArguments, ScalaInstance}


sealed abstract class CompilerCore
{
	final def apply(label: String, sources: Iterable[Path], classpath: Iterable[Path], outputDirectory: Path, scalaOptions: Seq[String], log: Logger): Option[String] =
		apply(label, sources, classpath, outputDirectory, scalaOptions, Nil, CompileOrder.Mixed, log)
	final def apply(label: String, sources: Iterable[Path], classpath: Iterable[Path], outputDirectory: Path, scalaOptions: Seq[String], javaOptions: Seq[String], order: CompileOrder.Value, log: Logger): Option[String] =
	{
			def filteredSources(extension: String) = sources.filter(_.name.endsWith(extension))
			def process(label: String, sources: Iterable[_], act: => Unit) =
				() => if(sources.isEmpty) log.debug("No " + label + " sources.") else act

		val javaSources = Path.getFiles(filteredSources(".java"))
		val scalaSources = Path.getFiles( if(order == CompileOrder.Mixed) sources else filteredSources(".scala") )
		val classpathSet = Path.getFiles(classpath)
		val scalaCompile = process("Scala", scalaSources, processScala(scalaSources, classpathSet, outputDirectory.asFile, scalaOptions, log) )
		val javaCompile = process("Java", javaSources, processJava(javaSources, classpathSet, outputDirectory.asFile, javaOptions, log))
		doCompile(label, sources, outputDirectory, order, log)(javaCompile, scalaCompile)
	}
	protected def doCompile(label: String, sources: Iterable[Path], outputDirectory: Path, order: CompileOrder.Value, log: Logger)(javaCompile: () => Unit, scalaCompile: () => Unit) =
	{
		log.info(actionStartMessage(label))
		if(sources.isEmpty)
		{
			log.info(actionNothingToDoMessage)
			None
		}
		else
		{
			FileUtilities.createDirectory(outputDirectory.asFile, log) orElse
				(try
				{
					val (first, second) = if(order == CompileOrder.JavaThenScala) (javaCompile, scalaCompile) else (scalaCompile, javaCompile)
					first()
					second()
					log.info(actionSuccessfulMessage)
					None
				}
				catch { case e: xsbti.CompileFailed => Some(e.toString) })
		}
	}
	def actionStartMessage(label: String): String
	def actionNothingToDoMessage: String
	def actionSuccessfulMessage: String
	protected def processScala(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger): Unit
	protected def processJava(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger): Unit
}

sealed abstract class CompilerBase extends CompilerCore
{
	def actionStartMessage(label: String) = "Compiling " + label + " sources..."
	val actionNothingToDoMessage = "Nothing to compile."
	val actionSuccessfulMessage = "Compilation successful."
}

// The following code is based on scala.tools.nsc.Main and scala.tools.nsc.ScalaDoc
// Copyright 2005-2008 LAMP/EPFL
// Original author: Martin Odersky

final class Compile(maximumErrors: Int, compiler: AnalyzingCompiler, analysisCallback: AnalysisCallback, baseDirectory: Path) extends CompilerBase with WithArgumentFile
{
	protected def processScala(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger)
	{
		val callbackInterface = new AnalysisInterface(analysisCallback, baseDirectory, outputDirectory)
		compiler(Set() ++ sources, Set() ++ classpath, outputDirectory, options, callbackInterface, maximumErrors, log)
	}
}
final class Scaladoc(maximumErrors: Int, compiler: AnalyzingCompiler) extends CompilerCore
{
	protected def processScala(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger): Unit =
		compiler.doc(sources, classpath, outputDirectory, options, maximumErrors, log)
	protected def processJava(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger) = ()

	def actionStartMessage(label: String) = "Generating API documentation for " + label + " sources..."
	val actionNothingToDoMessage = "No sources specified."
	val actionSuccessfulMessage = "API documentation generation successful."
	def actionUnsuccessfulMessage = "API documentation generation unsuccessful."
}
final class Console(compiler: AnalyzingCompiler) extends NotNull
{
	/** Starts an interactive scala interpreter session with the given classpath.*/
	def apply(classpath: Iterable[Path], log: Logger): Option[String] =
		apply(classpath, Nil, "", log)
	def apply(classpath: Iterable[Path], options: Seq[String], initialCommands: String, log: Logger): Option[String] =
	{
		def console0 = compiler.console(Path.getFiles(classpath), options, initialCommands, log)
		JLine.withJLine( Run.executeTrapExit(console0, log) )
	}
}
