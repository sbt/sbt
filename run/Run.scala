/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah, Vesa Vilhonen
 */
package sbt

import java.io.File
import java.net.{URL, URLClassLoader}
import java.lang.reflect.{Method, Modifier}
import Modifier.{isPublic, isStatic}
import classpath.ClasspathUtilities

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
class Run(instance: ScalaInstance) extends ScalaRun
{
	/** Runs the class 'mainClass' using the given classpath and options using the scala runner.*/
	def run(mainClass: String, classpath: Iterable[Path], options: Seq[String], log: Logger) =
	{
		log.info("Running " + mainClass + " " + options.mkString(" "))

		def execute = 
			try { run0(mainClass, classpath, options, log) }
			catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }

		Run.executeTrapExit( execute, log )
	}
	private def run0(mainClassName: String, classpath: Iterable[Path], options: Seq[String], log: Logger)
	{
		log.debug("  Classpath:\n\t" + classpath.mkString("\n\t"))
		val (loader, tempDir) = ClasspathUtilities.makeLoader(classpath, instance)
		try 
		{
			val main = getMainMethod(mainClassName, loader)
			invokeMain(loader, main, options)
		}
		finally { IO.delete(tempDir asFile) }
	}
	private def invokeMain(loader: ClassLoader, main: Method, options: Seq[String])
	{
		val currentThread = Thread.currentThread
		val oldLoader = Thread.currentThread.getContextClassLoader()
		currentThread.setContextClassLoader(loader)
		try { main.invoke(null, options.toArray[String].asInstanceOf[Array[String]] ) }
		finally { currentThread.setContextClassLoader(oldLoader) }
	}
	def getMainMethod(mainClassName: String, loader: ClassLoader) =
	{
		val mainClass = Class.forName(mainClassName, true, loader)
		val method = mainClass.getMethod("main", classOf[Array[String]])
		val modifiers = method.getModifiers
		if(!isPublic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not public")
		if(!isStatic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not static")
		method
	}
}

/** This module is an interface to starting the scala interpreter or runner.*/
object Run
{
	def run(mainClass: String, classpath: Iterable[Path], options: Seq[String], log: Logger)(implicit runner: ScalaRun) =
		runner.run(mainClass, classpath, options, log)
		
	/** Executes the given function, trapping calls to System.exit. */
	def executeTrapExit(f: => Unit, log: Logger): Option[String] =
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
}