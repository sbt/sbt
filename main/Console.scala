/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import compiler.AnalyzingCompiler

final class Console(compiler: AnalyzingCompiler)
{
	/** Starts an interactive scala interpreter session with the given classpath.*/
	def apply(classpath: Seq[File], log: Logger): Option[String] =
		apply(classpath, Nil, "", log)

	def apply(classpath: Seq[File], options: Seq[String], initialCommands: String, log: Logger): Option[String] =
		apply(classpath, options, initialCommands)(None, Nil)(log)
	
	def apply(classpath: Seq[File], options: Seq[String], loader: ClassLoader, initialCommands: String)(bindings: (String, Any)*)(implicit log: Logger): Option[String] =
		apply(classpath, options, initialCommands)(Some(loader), bindings)
	
	def apply(classpath: Seq[File], options: Seq[String], initialCommands: String)(loader: Option[ClassLoader], bindings: Seq[(String, Any)])(implicit log: Logger): Option[String] =
	{
		def console0 = compiler.console(classpath, options, initialCommands, log)(loader, bindings)
		JLine.withJLine( Run.executeTrapExit(console0, log) )
	}
}
object Console
{
	val SbtInitial = "import sbt._, Process._, current._"
	
	def apply(conf: build.Compile)(implicit log: Logger): Console = new Console( compiler(conf) )
	def apply(conf: Compile.Inputs): Console = new Console( conf.compilers.scalac )

	def compiler(conf: build.Compile)(implicit log: Logger): AnalyzingCompiler =
	{
		val componentManager = new ComponentManager(conf.launcher.globalLock, conf.configuration.provider.components, log)
		new AnalyzingCompiler(conf.instance, componentManager, log)
	}
	def sbt(state: State, extra: String)(implicit log: Logger)
	{
		val extracted = Project extract state
		val bindings = ("state" -> state) :: ("extracted" -> extracted ) :: Nil
		val unit = extracted.currentUnit
		val compiler = Compile.compilers(state.configuration, log).scalac
		val imports = Load.getImports(unit.unit) ++ Load.importAll(bindings.map(_._1))
		val importString = imports.mkString("", ";\n", ";\n\n")
		val initCommands = importString + extra
		val loader = classOf[State].getClassLoader				
		(new Console(compiler))(unit.classpath, Nil, initCommands)(Some(loader), bindings)
	}
}


final class Scaladoc(maximumErrors: Int, compiler: AnalyzingCompiler)
{
	final def apply(label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String])(implicit log: Logger)
	{
		log.info(actionStartMessage(label))
		if(sources.isEmpty)
			log.info(ActionNothingToDoMessage)
		else
		{
			IO.createDirectory(outputDirectory)
			compiler.doc(sources, classpath, outputDirectory, options, maximumErrors, log)
			log.info(ActionSuccessfulMessage)
		}
	}
	def actionStartMessage(label: String) = "Generating API documentation for " + label + " sources..."
	val ActionNothingToDoMessage = "No sources specified."
	val ActionSuccessfulMessage = "API documentation generation successful."
}