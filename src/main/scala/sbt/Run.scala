/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah, Vesa Vilhonen
 */
package sbt

import scala.tools.nsc.{GenericRunnerCommand, Interpreter, InterpreterLoop, ObjectRunner, Settings}
import scala.tools.nsc.interpreter.InteractiveReader
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.ClassPath

import java.io.File
import java.net.{URL, URLClassLoader}

trait ScalaRun
{
	def run(mainClass: String, classpath: Iterable[Path], options: Seq[String], log: Logger): Option[String]
}
class ForkRun(config: ForkScalaRun) extends ScalaRun
{
	def run(mainClass: String, classpath: Iterable[Path], options: Seq[String], log: Logger): Option[String] =
	{
		val scalaOptions = classpathOption(classpath) ::: mainClass :: options.toList
		val exitCode = config.outputStrategy match {
			case Some(strategy) => Fork.scala(config.javaHome, config.runJVMOptions, config.scalaJars, scalaOptions, config.workingDirectory, strategy)
			case None => Fork.scala(config.javaHome, config.runJVMOptions, config.scalaJars, scalaOptions, config.workingDirectory, LoggedOutput(log))
		}
		processExitCode(exitCode, "runner")
	}
	private def classpathOption(classpath: Iterable[Path]) = "-cp" :: Path.makeString(classpath) :: Nil
	private def processExitCode(exitCode: Int, label: String) =
	{
		if(exitCode == 0)
			None
		else
			Some("Nonzero exit code returned from " + label + ": " + exitCode)
	}
}
class Run(compiler: xsbt.AnalyzingCompiler) extends ScalaRun
{
	/** Runs the class 'mainClass' using the given classpath and options using the scala runner.*/
	def run(mainClass: String, classpath: Iterable[Path], options: Seq[String], log: Logger) =
	{
		def execute = compiler.run(Set() ++ classpath.map(_.asFile), mainClass, options, log)
		Run.executeTrapExit( execute, log )
	}
}

/** This module is an interface to starting the scala interpreter or runner.*/
object Run
{
	/** Executes the given function, trapping calls to System.exit. */
	private[sbt] def executeTrapExit(f: => Unit, log: Logger): Option[String] =
	{
		val exitCode = TrapExit(f, log)
		if(exitCode == 0)
		{
			log.debug("Exited with code 0")
			None
		}
		else
			Some("Nonzero exit code: " + exitCode)
	}
	/** Create a settings object and execute the provided function if the settings are created ok.*/
	private def createSettings(log: Logger)(f: Settings => Option[String]) =
	{
		val command = new GenericRunnerCommand(Nil, message => log.error(message))
		if(command.ok)
			f(command.settings)
		else
			Some(command.usageMsg)
	}

	/** Starts a Scala interpreter session with 'project' bound to the value 'current' in the console
	* and the following two lines executed:
	*   import sbt._
	*   import current._
	*/
	def projectConsole(project: Project): Option[String] =
	{
		import project.log
		createSettings(log) { interpreterSettings =>
		createSettings(log) { compilerSettings =>
			log.info("Starting scala interpreter with project definition " + project.name + " ...")
			log.info("")
			Control.trapUnit("Error during session: ", log)
			{
				JLine.withJLine {
					val loop = new ProjectInterpreterLoop(compilerSettings, project)
					executeTrapExit(loop.main(interpreterSettings), log)
				}
			}
		}}
	}
	/** A custom InterpreterLoop with the purpose of creating an interpreter with Project 'project' bound to the value 'current',
	* and the following three lines interpreted:
	*   import sbt._
	*   import Process._
	*   import current._.
	* To do this,
	* 1)  The compiler uses a different settings instance: 'compilerSettings', which will have its classpath set to include the classpath
	*    of the loader that loaded 'project'.  The compiler can then find the classes it needs to compile code referencing the project.
	* 2) The parent class loader for the interpreter is the loader that loaded the project, so that the project can be bound to a variable
	*    in the interpreter.
	*/
	private class ProjectInterpreterLoop(compilerSettings: Settings, project: Project) extends InterpreterLoop
	{
		override def createInterpreter()
		{
			val projectLoader = project.getClass.getClassLoader
			val launcherJar = FileUtilities.classLocationFile[xsbti.Launcher]
			val app = project.info.app
			val classpathFiles = app.mainClasspath ++ app.scalaProvider.jars ++ Array(launcherJar)
			compilerSettings.classpath.value = classpathFiles.map(_.getAbsolutePath).mkString(File.pathSeparator)
			project.log.debug("  Compiler classpath: " + compilerSettings.classpath.value)

			in = InteractiveReader.createDefault()
			interpreter = new Interpreter(settings)
			{
				override protected def parentClassLoader = projectLoader
				override protected def newCompiler(settings: Settings, reporter: Reporter) = super.newCompiler(compilerSettings, reporter)
			}
			interpreter.setContextClassLoader()
			interpreter.bind("current", project.getClass.getName, project)
			interpreter.interpret("import sbt._")
			interpreter.interpret("import Process._")
			interpreter.interpret("import current._")
		}
	}
}
