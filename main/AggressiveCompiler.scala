/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import xsbt.{AnalyzingCompiler, CompileLogger, ScalaInstance}
	import java.io.File
	import System.{currentTimeMillis => now}
	import Path._
	import GlobFilter._

class AggressiveCompiler extends xsbti.AppMain
{
	final def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		val args = configuration.arguments.map(_.trim).toList
		readLine("Press enter to compile... ")
		val start = now
		val success = run(args, configuration.baseDirectory, configuration.provider)
		println("Compiled in " + ((now - start) / 1000.0) + " s")
		run(configuration)
	}
	def run(args: List[String], cwd: Path, app: xsbti.AppProvider): Boolean =
	{
		val launcher = app.scalaProvider.launcher
		val sources = cwd ** "*.scala"
		val target = cwd / "target"
		val outputDirectory = target / "classes"
		val classpath = outputDirectory +++ (cwd * "*.jar") +++(cwd * (-"project")).descendentsExcept( "*.jar", "project" || HiddenFileFilter)
		val cacheDirectory = target / "cache"
		val options = args.tail.toSeq
		val log = new ConsoleLogger with CompileLogger with sbt.IvyLogger
		val componentManager = new ComponentManager(launcher.globalLock, app.components, log)
		val compiler = new AnalyzingCompiler(ScalaInstance(args.head, launcher), componentManager, log)

		val agg = new AggressiveCompile(cacheDirectory)
		try { agg(sources.get.toSeq, classpath.get.toSeq, outputDirectory, options, compiler, log); true }
		catch { case e: Exception => handleException(e); false }
	}
	def handleException(e: Throwable) =
	{
		if(!e.isInstanceOf[xsbti.CompileFailed])
		{
			e.printStackTrace
			System.err.println(e.toString)
		}
	}
}