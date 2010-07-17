/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import sbt.compile.{AnalyzingCompiler, JavaCompiler}
	import sbt.build.AggressiveCompile
	import java.io.File
	import System.{currentTimeMillis => now}
	import Path._
	import GlobFilter._

class AggressiveCompiler extends xsbti.AppMain
{
	final def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		val args = configuration.arguments.map(_.trim).toList
		val command = readLine("Press enter to compile... ").trim()
		val start = now
		val success = run(command, args, configuration.baseDirectory, configuration.provider)
		println("Compiled in " + ((now - start) / 1000.0) + " s")
		run(configuration)
	}
	def run(command: String, args: List[String], cwd: Path, app: xsbti.AppProvider): Boolean =
	{
		val launcher = app.scalaProvider.launcher
		val sources = cwd ** ("*.scala" | "*.java")
		val target = cwd / "target"
		val javaBaseDirs = cwd :: Nil
		val outputDirectory = target / "classes"
		val classpath = outputDirectory +++ (cwd * "*.jar") +++(cwd * (-"project")).descendentsExcept( "*.jar", "project" || HiddenFileFilter)
		val cacheDirectory = target / "cache"
		val options = args.tail.toSeq
		val log = new ConsoleLogger with Logger with sbt.IvyLogger
		val componentManager = new ComponentManager(launcher.globalLock, app.components, log)
		val compiler = new AnalyzingCompiler(ScalaInstance(args.head, launcher), componentManager, log)
		val javac = JavaCompiler.directOrFork(compiler.cp, compiler.scalaInstance)( (args: Seq[String], log: Logger) => Process("javac", args) ! log )

		val agg = new AggressiveCompile(cacheDirectory)
		try
		{
			val analysis = agg(compiler, javac, sources.get.toSeq, classpath.get.toSeq, outputDirectory, javaBaseDirs, options)(log)
			processResult(analysis, command)
			true
		}
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
	def processResult(analysis: inc.Analysis, command: String)
	{
		if(command.isEmpty) ()
		else
		{
			xsbt.api.ParseType.parseType(command) match
			{
				case Left(err) => println("Error parsing type: " + err)
				case Right(tpe) => analysis.apis.internal.values.foreach(processAPI)
			}
		}
	}
	def processAPI(api: xsbti.api.Source)
	{
		val d = new inc.Discovery(Set("scala.Enumeration", "scala.AnyRef", "scala.ScalaObject"), Set("scala.deprecated", "scala.annotation.tailrec"))
		println(d(api.definitions).map { case (a, b) => (a.name, b) } )
	}
}