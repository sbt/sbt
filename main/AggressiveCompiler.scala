/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt

	import java.io.File

class AggressiveCompiler extends xsbti.AppMain
{
	final def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		System.setProperty("sbt.api.enable", "true")
		val args = configuration.arguments.map(_.trim).toList
		readLine("Press enter to compile... ")
		val start = System.currentTimeMillis
		val success = run(args, configuration.baseDirectory, configuration.provider)
		val end = System.currentTimeMillis
		println("Compiled in " + ((end - start) / 1000.0) + " s")
		run(configuration)
	}
	def run(args: List[String], cwd: File, app: xsbti.AppProvider): Boolean =
	{
			import Paths._
			import GlobFilter._
		val launcher = app.scalaProvider.launcher
		val sources = Task(cwd ** "*.scala")
		val outputDirectory = Task(cwd / "target" / "classes")
		val classpath = outputDirectory map { _ ++ (cwd * "*.jar") ++(cwd * (-"project")).descendentsExcept( "*.jar", "project" || HiddenFileFilter) }
		val cacheDirectory = cwd / "target" / "cache"
		val options = Task(args.tail.toSeq)
		val log = new ConsoleLogger with CompileLogger with sbt.IvyLogger { def verbose(msg: => String) = debug(msg) }
		val componentManager = new sbt.ComponentManager(launcher.globalLock, app.components, log)
		val compiler = Task(new AnalyzingCompiler(ScalaInstance(args.head, launcher), componentManager))
		val compileTask = AggressiveCompile(sources, classpath, outputDirectory, options, cacheDirectory, compiler, log)
		
		try { TaskRunner(compileTask.task); true }
		catch
		{
			case w: TasksFailed => w.failures.foreach { f => handleException(f.exception) }; false
			case e: Exception => handleException(e); false
		}
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