/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
/*package sbt

import java.io.File
import compile.AnalyzingCompiler

final class Console(compiler: AnalyzingCompiler) extends NotNull
{
	/** Starts an interactive scala interpreter session with the given classpath.*/
	def apply(classpath: Iterable[File], log: Logger): Option[String] =
		apply(classpath, Nil, "", log)

	def apply(classpath: Iterable[File], options: Seq[String], initialCommands: String, log: Logger): Option[String] =
	{
		def console0 = compiler.console(Path.getFiles(classpath), options, initialCommands, log)
		JLine.withJLine( Run.executeTrapExit(console0, log) )
	}
}*/