/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.nio.file.Path as NioPath
import java.lang.reflect.Method
import java.lang.reflect.Modifier.{ isPrivate, isPublic, isStatic }
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.{ ClasspathFilter, ClasspathUtil }
import sbt.internal.util.MessageOnlyException
import sbt.io.Path
import sbt.util.Logger

import scala.sys.process.Process
import scala.util.control.NonFatal
import scala.util.{ Failure, Properties, Success, Try }

sealed trait ScalaRun:
  def run(mainClass: String, classpath: Seq[NioPath], options: Seq[String], log: Logger): Try[Unit]

class ForkRun(config: ForkOptions) extends ScalaRun {
  def run(
      mainClass: String,
      classpath: Seq[NioPath],
      options: Seq[String],
      log: Logger
  ): Try[Unit] = {
    log.info(s"running (fork) $mainClass ${Run.runOptionsStr(options)}")
    val c = configLogged(log)
    val scalaOpts = scalaOptions(mainClass, classpath, options)
    val exitCode =
      try Fork.java(c, scalaOpts)
      catch {
        case _: InterruptedException =>
          log.warn("run canceled")
          1
      }
    Run.processExitCode(exitCode, "runner")
  }

  def fork(
      mainClass: String,
      classpath: Seq[NioPath],
      options: Seq[String],
      log: Logger
  ): Process = {
    log.info(s"running (fork) $mainClass ${Run.runOptionsStr(options)}")

    val c = configLogged(log)
    val scalaOpts = scalaOptions(mainClass, classpath, options)

    // fork with Java because Scala introduces an extra class loader (#702)
    Fork.java.fork(c, scalaOpts)
  }

  private def configLogged(log: Logger): ForkOptions = {
    if (config.outputStrategy.isDefined || config.connectInput) config
    else config.withOutputStrategy(OutputStrategy.LoggedOutput(log))
  }

  private def scalaOptions(
      mainClass: String,
      classpath: Seq[NioPath],
      options: Seq[String],
  ): Seq[String] =
    "-classpath" :: Path.makeString(classpath.map(_.toFile())) :: mainClass :: options.toList
}

class Run(private[sbt] val newLoader: Seq[NioPath] => ClassLoader, trapExit: Boolean)
    extends ScalaRun {
  def this(instance: ScalaInstance, trapExit: Boolean, nativeTmp: File) =
    this(
      (cp: Seq[NioPath]) => ClasspathUtil.makeLoader(cp, instance, nativeTmp.toPath),
      trapExit
    )

  private[sbt] def runWithLoader(
      loader: ClassLoader,
      classpath: Seq[NioPath],
      mainClass: String,
      options: Seq[String],
      log: Logger
  ): Try[Unit] = {
    log.info(s"running $mainClass ${Run.runOptionsStr(options)}")

    def execute(): Unit =
      try {
        log.debug("  Classpath:\n\t" + classpath.mkString("\n\t"))
        val main = detectMainMethod(mainClass, loader)
        invokeMain(loader, main, options)
      } catch {
        case e: java.lang.reflect.InvocationTargetException =>
          e.getCause match {
            case ex: ClassNotFoundException =>
              val className = ex.getMessage
              try {
                loader.loadClass(className)
                val msg =
                  s"$className is on the project classpath but not visible to the ClassLoader " +
                    "that attempted to load it.\n" +
                    "See https://www.scala-sbt.org/1.x/docs/In-Process-Classloaders.html for " +
                    "further information."
                log.error(msg)
              } catch { case NonFatal(_) => }
              throw ex
            case ex => throw ex
          }
      }
    def directExecute(): Try[Unit] =
      Try(execute()) recover { case NonFatal(e) =>
        // bgStop should not print out stack trace
        // log.trace(e)
        throw e
      }

    if (trapExit) Run.executeSuccess(execute())
    else directExecute()
  }

  /** Runs the class 'mainClass' using the given classpath and options using the scala runner. */
  def run(
      mainClass: String,
      classpath: Seq[NioPath],
      options: Seq[String],
      log: Logger
  ): Try[Unit] = {
    val loader = newLoader(classpath)
    try runWithLoader(loader, classpath, mainClass, options, log)
    finally
      loader match
        case ac: AutoCloseable  => ac.close()
        case c: ClasspathFilter => c.close()
        case _                  =>
  }
  private def invokeMain(
      loader: ClassLoader,
      main: DetectedMain,
      options: Seq[String]
  ): Unit = {
    val currentThread = Thread.currentThread
    val oldLoader = Thread.currentThread.getContextClassLoader
    currentThread.setContextClassLoader(loader)
    try {
      if (main.isStatic) {
        if (Run.isJava25Plus) {
          main.method.setAccessible(true)
        }
        if (main.parameterCount > 0) main.method.invoke(null, options.toArray[String])
        else main.method.invoke(null)
      } else {
        val constructor = main.mainClass.getDeclaredConstructor()
        if (Run.isJava25Plus) {
          constructor.setAccessible(true)
        }
        val ref = constructor.newInstance().asInstanceOf[AnyRef]
        if (main.parameterCount > 0) main.method.invoke(ref, options.toArray[String])
        else main.method.invoke(ref)
      }
      ()
    } catch {
      case t: Throwable =>
        t.getCause match {
          case e: java.lang.IllegalAccessError =>
            val msg = s"Error running $main.\n$e\n" +
              "If using a layered classloader, this can occur if jvm package private classes are " +
              "accessed across layers. This can be fixed by changing to the Flat or " +
              "ScalaInstance class loader layering strategies."
            throw new IllegalAccessError(msg)
          case _ => throw t
        }
    } finally {
      currentThread.setContextClassLoader(oldLoader)
    }
  }
  def getMainMethod(mainClassName: String, loader: ClassLoader): Method =
    detectMainMethod(mainClassName, loader).method

  private def detectMainMethod(mainClassName: String, loader: ClassLoader) = {
    val mainClass = Class.forName(mainClassName, true, loader)
    if (Run.isJava25Plus) {
      val method =
        try {
          mainClass.getMethod("main", classOf[Array[String]])
        } catch {
          case _: NoSuchMethodException =>
            try {
              mainClass.getMethod("main")
            } catch {
              case _: NoSuchMethodException =>
                try {
                  mainClass.getDeclaredMethod("main", classOf[Array[String]])
                } catch {
                  case _: NoSuchMethodException =>
                    mainClass.getDeclaredMethod("main")
                }
            }
        }
      val modifiers = method.getModifiers
      if (isPrivate(modifiers)) {
        throw new NoSuchMethodException(s"${mainClassName}.main is private")
      }
      method.setAccessible(true)
      DetectedMain(mainClass, method, isStatic(modifiers), method.getParameterCount())
    } else {
      val method = mainClass.getMethod("main", classOf[Array[String]])
      // jvm allows the actual main class to be non-public and to run a method in the non-public class,
      //  we need to make it accessible
      method.setAccessible(true)
      val modifiers = method.getModifiers
      if (!isPublic(modifiers))
        throw new NoSuchMethodException(mainClassName + ".main is not public")
      if (!isStatic(modifiers))
        throw new NoSuchMethodException(mainClassName + ".main is not static")
      DetectedMain(mainClass, method, isStatic = true, method.getParameterCount())
    }
  }
  private case class DetectedMain(
      mainClass: Class[?],
      method: Method,
      isStatic: Boolean,
      parameterCount: Int
  )
}

/** This module is an interface to starting the scala interpreter or runner. */
object Run:
  def run(mainClass: String, classpath: Seq[NioPath], options: Seq[String], log: Logger)(using
      runner: ScalaRun
  ) =
    runner.run(mainClass, classpath, options, log)

  private[sbt] def executeSuccess(f: => Unit): Try[Unit] = {
    f
    Success(())
  }

  // quotes the option that includes a whitespace
  // https://github.com/sbt/sbt/issues/4834
  private[sbt] def runOptionsStr(options: Seq[String]): String =
    (options map {
      case str if str.contains(" ") => "\"" + str + "\""
      case str                      => str
    }).mkString(" ")

  private[sbt] def processExitCode(exitCode: Int, label: String): Try[Unit] =
    if (exitCode == 0) Success(())
    else
      Failure(
        new MessageOnlyException(
          s"""nonzero exit code returned from $label: $exitCode""".stripMargin
        )
      )

  private[sbt] lazy val isJava25Plus: Boolean = Properties.isJavaAtLeast("25")
end Run
