/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
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
	def console(classpath: Iterable[Path], log: Logger): Option[String]
	def run(mainClass: String, classpath: Iterable[Path], options: Seq[String], log: Logger): Option[String]
}
class ForkRun(config: ForkScalaRun) extends ScalaRun
{
	def console(classpath: Iterable[Path], log: Logger): Option[String] =
	{
		error("Forking the interpreter is not implemented.")
		//val exitCode = Fork.scala(config.javaHome, config.runJVMOptions, config.scalaJars, classpathOption(classpath), config.workingDirectory, log)
		//processExitCode(exitCode, "interpreter")
	}
	def run(mainClass: String, classpath: Iterable[Path], options: Seq[String], log: Logger): Option[String] =
	{
		val scalaOptions = classpathOption(classpath) ::: mainClass :: options.toList
		val exitCode = Fork.scala(config.javaHome, config.runJVMOptions, config.scalaJars, scalaOptions, config.workingDirectory, log)
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

/** This module is an interface to starting the scala interpreter or runner.*/
object Run extends ScalaRun
{
	/** Starts an interactive scala interpreter session with the given classpath.*/
	def console(classpath: Iterable[Path], log: Logger) =
		createSettings(log)
		{
			(settings: Settings) =>
			{
				settings.classpath.value = Path.makeString(classpath)
				log.info("Starting scala interpreter...")
				log.debug("  Classpath: " + settings.classpath.value)
				log.info("")
				Control.trapUnit("Error during session: ", log)
				{
					JLine.withJLine {
						val loop = new InterpreterLoop
						executeTrapExit(loop.main(settings), log)
					}
				}
			}
		}
	/** Executes the given function, trapping calls to System.exit. */
	private def executeTrapExit(f: => Unit, log: Logger): Option[String] =
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
	/** Runs the class 'mainClass' using the given classpath and options using the scala runner.*/
	def run(mainClass: String, classpath: Iterable[Path], options: Seq[String], log: Logger) =
	{
		createSettings(log)
		{
			(settings: Settings) =>
			{
				Control.trapUnit("Error during run: ", log)
				{
					val classpathURLs = classpath.map(_.asURL).toList
					val bootClasspath = FileUtilities.pathSplit(settings.bootclasspath.value)
					val extraURLs =
						for(pathString <- bootClasspath if pathString.length > 0) yield
							(new java.io.File(pathString)).toURI.toURL
					log.info("Running " + mainClass + " ...")
					log.debug("  Classpath:" + (classpathURLs ++ extraURLs).mkString("\n\t", "\n\t",""))
					executeTrapExit( ObjectRunner.run(classpathURLs ++ extraURLs, mainClass, options.toList), log )
				}
			}
		}
	}
	/** If mainClassOption is None, then the interactive scala interpreter is started with the given classpath.
	* Otherwise, the class wrapped by Some is run using the scala runner with the given classpath and
	* options. */
	def apply(mainClassOption: Option[String], classpath: Iterable[Path], options: Seq[String], log: Logger) =
	{
		mainClassOption match
		{
			case Some(mainClass) => run(mainClass, classpath, options, log)
			case None => console(classpath, log)
		}
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
	* and the following two lines interpreted:
	*   import sbt._
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
			val loader = project.getClass.getClassLoader.asInstanceOf[URLClassLoader]
			compilerSettings.classpath.value = loader.getURLs.flatMap(ClasspathUtilities.asFile).map(_.getAbsolutePath).mkString(File.pathSeparator)
			project.log.debug("  Compiler classpath: " + compilerSettings.classpath.value)
			
			in = InteractiveReader.createDefault()
			interpreter = new Interpreter(settings)
			{
				override protected def parentClassLoader = loader
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