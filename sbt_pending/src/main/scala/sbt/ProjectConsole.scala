/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt

import scala.tools.nsc.{GenericRunnerCommand, Interpreter, InterpreterLoop, ObjectRunner, Settings}
import scala.tools.nsc.interpreter.InteractiveReader
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.ClassPath

/** This module is an interface to starting the scala interpreter or runner.*/
object ProjectConsole
{
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
	def apply(project: Project): Option[String] =
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
	* 1)  The compiler uses a different settings instance: 'compilerSettings', which will have its classpath set to include
	*    the Scala compiler and library jars and the classpath used to compile the project.
	* 2) The parent class loader for the interpreter is the loader that loaded the project, so that the project can be bound to a variable
	*    in the interpreter.
	*/
	private class ProjectInterpreterLoop(compilerSettings: Settings, project: Project) extends InterpreterLoop
	{
		override def createInterpreter()
		{
			val projectLoader = project.getClass.getClassLoader
			val classpath = Project.getProjectClasspath(project)
			val fullClasspath = classpath.get ++ Path.fromFiles(project.info.app.scalaProvider.jars)
			compilerSettings.classpath.value = Path.makeString(fullClasspath)
			project.log.debug("  console-project classpath:\n\t" + fullClasspath.mkString("\n\t"))

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
