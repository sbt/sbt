/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import compile.AnalyzingCompiler

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
	val SbtInitial = "import sbt._; import Process._; import current._"
	
	def apply(conf: build.Compile)(implicit log: Logger): Console = new Console( compiler(conf) )
	def apply(conf: Compile.Inputs): Console = new Console( conf.compilers.scalac )

	def compiler(conf: build.Compile)(implicit log: Logger): AnalyzingCompiler =
	{
		val componentManager = new ComponentManager(conf.launcher.globalLock, conf.configuration.provider.components, log)
		new AnalyzingCompiler(conf.instance, componentManager, log)
	}
	def sbtDefault(conf: build.Compile, value: Any)(implicit log: Logger)
	{
		val c = new Console(compiler(conf))
		val loader = value.asInstanceOf[AnyRef].getClass.getClassLoader
		c.apply(conf.compileClasspath, Nil, loader, SbtInitial)("current" -> value)
	}
	def sbtDefault(conf: Compile.Inputs, value: Any)(implicit log: Logger)
	{
		val loader = value.asInstanceOf[AnyRef].getClass.getClassLoader
		Console(conf)(conf.config.classpath, Nil, loader, SbtInitial)("current" -> value)
	}
}


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
}