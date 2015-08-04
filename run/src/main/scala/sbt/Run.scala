/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah, Vesa Vilhonen
 */
package sbt

import java.io.File
import java.net.{ URL, URLClassLoader }
import java.lang.reflect.{ Method, Modifier }
import Modifier.{ isPublic, isStatic }
import classpath.ClasspathUtilities

trait ScalaRun {
  def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger): Option[String]
}
class ForkRun(config: ForkOptions) extends ScalaRun {
  @deprecated("Use the `ForkRun(ForkOptions) constructor`", "0.13.0")
  def this(options: ForkScalaRun) = this(ForkOptions(options.javaHome, options.outputStrategy, options.scalaJars.toSeq, options.workingDirectory, options.runJVMOptions, options.connectInput))

  def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger): Option[String] =
    {
      log.info("Running " + mainClass + " " + options.mkString(" "))

      val scalaOptions = classpathOption(classpath) ::: mainClass :: options.toList
      val configLogged = if (config.outputStrategy.isDefined) config else config.copy(outputStrategy = Some(LoggedOutput(log)))
      // fork with Java because Scala introduces an extra class loader (#702)
      val process = Fork.java.fork(configLogged, scalaOptions)
      def cancel() = {
        log.warn("Run canceled.")
        process.destroy()
        1
      }
      val exitCode = try process.exitValue() catch { case e: InterruptedException => cancel() }
      processExitCode(exitCode, "runner")
    }
  private def classpathOption(classpath: Seq[File]) = "-classpath" :: Path.makeString(classpath) :: Nil
  private def processExitCode(exitCode: Int, label: String) =
    {
      if (exitCode == 0)
        None
      else
        Some("Nonzero exit code returned from " + label + ": " + exitCode)
    }
}
class Run(instance: ScalaInstance, trapExit: Boolean, nativeTmp: File) extends ScalaRun {
  /** Runs the class 'mainClass' using the given classpath and options using the scala runner.*/
  def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger) =
    {
      log.info("Running " + mainClass + " " + options.mkString(" "))

      def execute() =
        try { run0(mainClass, classpath, options, log) }
        catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
      def directExecute() = try { execute(); None } catch { case e: Exception => log.trace(e); Some(e.toString) }

      if (trapExit) Run.executeTrapExit(execute(), log) else directExecute()
    }
  private def run0(mainClassName: String, classpath: Seq[File], options: Seq[String], log: Logger): Unit = {
    log.debug("  Classpath:\n\t" + classpath.mkString("\n\t"))
    val loader = ClasspathUtilities.makeLoader(classpath, instance, nativeTmp)
    val main = getMainMethod(mainClassName, loader)
    invokeMain(loader, main, options)
  }
  private def invokeMain(loader: ClassLoader, main: Method, options: Seq[String]): Unit = {
    val currentThread = Thread.currentThread
    val oldLoader = Thread.currentThread.getContextClassLoader
    currentThread.setContextClassLoader(loader)
    try { main.invoke(null, options.toArray[String]) }
    finally { currentThread.setContextClassLoader(oldLoader) }
  }
  def getMainMethod(mainClassName: String, loader: ClassLoader) =
    {
      val mainClass = Class.forName(mainClassName, true, loader)
      val method = mainClass.getMethod("main", classOf[Array[String]])
      // jvm allows the actual main class to be non-public and to run a method in the non-public class,
      //  we need to make it accessible
      method.setAccessible(true)
      val modifiers = method.getModifiers
      if (!isPublic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not public")
      if (!isStatic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not static")
      method
    }
}

/** This module is an interface to starting the scala interpreter or runner.*/
object Run {
  def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger)(implicit runner: ScalaRun) =
    runner.run(mainClass, classpath, options, log)

  /** Executes the given function, trapping calls to System.exit. */
  def executeTrapExit(f: => Unit, log: Logger): Option[String] =
    {
      val exitCode = TrapExit(f, log)
      if (exitCode == 0) {
        log.debug("Exited with code 0")
        None
      } else
        Some("Nonzero exit code: " + exitCode)
    }
}
