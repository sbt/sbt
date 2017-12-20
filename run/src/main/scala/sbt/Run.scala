/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import java.lang.reflect.{ Method, Modifier }
import Modifier.{ isPublic, isStatic }
import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.internal.inc.ScalaInstance

import sbt.io.Path

import sbt.util.Logger
import scala.util.{ Try, Success, Failure }
import scala.util.control.NonFatal
import scala.sys.process.Process

sealed trait ScalaRun {
  def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger): Try[Unit]
}
class ForkRun(config: ForkOptions) extends ScalaRun {
  def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger): Try[Unit] = {
    def processExitCode(exitCode: Int, label: String): Try[Unit] =
      if (exitCode == 0) Success(())
      else
        Failure(
          new RuntimeException(
            s"""Nonzero exit code returned from $label: $exitCode""".stripMargin))
    val process = fork(mainClass, classpath, options, log)
    def cancel() = {
      log.warn("Run canceled.")
      process.destroy()
      1
    }
    val exitCode = try process.exitValue()
    catch { case _: InterruptedException => cancel() }
    processExitCode(exitCode, "runner")
  }

  def fork(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger): Process = {
    log.info("Running (fork) " + mainClass + " " + options.mkString(" "))

    val scalaOptions = classpathOption(classpath) ::: mainClass :: options.toList
    val configLogged =
      if (config.outputStrategy.isDefined) config
      else config.withOutputStrategy(OutputStrategy.LoggedOutput(log))
    // fork with Java because Scala introduces an extra class loader (#702)
    Fork.java.fork(configLogged, scalaOptions)
  }
  private def classpathOption(classpath: Seq[File]) =
    "-classpath" :: Path.makeString(classpath) :: Nil
}
class Run(instance: ScalaInstance, trapExit: Boolean, nativeTmp: File) extends ScalaRun {

  /** Runs the class 'mainClass' using the given classpath and options using the scala runner.*/
  def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger): Try[Unit] = {
    log.info("Running " + mainClass + " " + options.mkString(" "))

    def execute() =
      try { run0(mainClass, classpath, options, log) } catch {
        case e: java.lang.reflect.InvocationTargetException => throw e.getCause
      }
    def directExecute(): Try[Unit] =
      Try(execute()) recover {
        case NonFatal(e) =>
          // bgStop should not print out stack trace
          // log.trace(e)
          throw e
      }
    // try { execute(); None } catch { case e: Exception => log.trace(e); Some(e.toString) }

    if (trapExit) Run.executeTrapExit(execute(), log)
    else directExecute()
  }
  private def run0(mainClassName: String,
                   classpath: Seq[File],
                   options: Seq[String],
                   log: Logger): Unit = {
    log.debug("  Classpath:\n\t" + classpath.mkString("\n\t"))
    val loader = ClasspathUtilities.makeLoader(classpath, instance, nativeTmp)
    val main = getMainMethod(mainClassName, loader)
    invokeMain(loader, main, options)
  }
  private def invokeMain(loader: ClassLoader, main: Method, options: Seq[String]): Unit = {
    val currentThread = Thread.currentThread
    val oldLoader = Thread.currentThread.getContextClassLoader
    currentThread.setContextClassLoader(loader)
    try { main.invoke(null, options.toArray[String]) } finally {
      currentThread.setContextClassLoader(oldLoader)
    }
  }
  def getMainMethod(mainClassName: String, loader: ClassLoader) = {
    val mainClass = Class.forName(mainClassName, true, loader)
    val method = mainClass.getMethod("main", classOf[Array[String]])
    // jvm allows the actual main class to be non-public and to run a method in the non-public class,
    //  we need to make it accessible
    method.setAccessible(true)
    val modifiers = method.getModifiers
    if (!isPublic(modifiers))
      throw new NoSuchMethodException(mainClassName + ".main is not public")
    if (!isStatic(modifiers))
      throw new NoSuchMethodException(mainClassName + ".main is not static")
    method
  }
}

/** This module is an interface to starting the scala interpreter or runner.*/
object Run {
  def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger)(
      implicit runner: ScalaRun) =
    runner.run(mainClass, classpath, options, log)

  /** Executes the given function, trapping calls to System.exit. */
  def executeTrapExit(f: => Unit, log: Logger): Try[Unit] = {
    val exitCode = TrapExit(f, log)
    if (exitCode == 0) {
      log.debug("Exited with code 0")
      Success(())
    } else Failure(new RuntimeException("Nonzero exit code: " + exitCode))
  }
}
