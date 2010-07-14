/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import java.io.File
import xsbt.{AnalyzingCompiler, CompileFailed}

final class Scaladoc(maximumErrors: Int, compiler: AnalyzingCompiler)
{
	final def apply(label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger)
	{
		log.info(actionStartMessage(label))
		if(sources.isEmpty)
			log.info(actionNothingToDoMessage)
		else
		{
			IO.createDirectory(outputDirectory)
			compiler.doc(sources, classpath, outputDirectory, options, maximumErrors, log)
			log.info(actionSuccessfulMessage)
		}
	}
	def actionStartMessage(label: String) = "Generating API documentation for " + label + " sources..."
	val actionNothingToDoMessage = "No sources specified."
	val actionSuccessfulMessage = "API documentation generation successful."
	def actionUnsuccessfulMessage = "API documentation generation unsuccessful."
}
final class Console(compiler: AnalyzingCompiler)
{
	/** Starts an interactive scala interpreter session with the given classpath.*/
	def apply(classpath: Seq[File])(implicit log: Logger): Option[String] =
		apply(classpath, Nil, "", log)
	def apply(classpath: Iterable[File], options: Seq[String], initialCommands: String)(implicit log: Logger): Option[String] =
	{
		def console0 = compiler.console(Path.getFiles(classpath), options, initialCommands, log)
		JLine.withJLine( Run.executeTrapExit(console0, log) )
	}
}
