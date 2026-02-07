/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.net.URL
import java.nio.file.Paths
import sbt.internal.inc.{
  AnalyzingCompiler,
  PlainVirtualFile,
  MappedFileConverter,
  ScalaInstance,
  ZincUtil
}
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.worker.{ ConsoleConfig, ScalaInstanceConfig }
import sbt.io.IO
import sbt.util.{ Level, Logger }
import sjsonnew.support.scalajson.unsafe.{ Parser, Converter }
import xsbti.compile.ClasspathOptionsUtil

/**
 * Entry point for the forked console. This class creates a Scala REPL
 * in the forked JVM with proper terminal support.
 */
class ConsoleMain:
  def run(config: ConsoleConfig): Unit =
    val si = scalaInstance(config.scalaInstanceConfig)
    val compiler = analyzingCompiler(config, si)
    given log: Logger = ConsoleMain.consoleLogger
    val classpathJars = config.classpathJars.map(Paths.get(_))
    val products = config.products.map(Paths.get(_))
    val cpFiles = products.map(_.toFile()) ++ classpathJars.map(_.toFile())
    IO.withTemporaryDirectory: tempDir =>
      val fullCp = cpFiles ++ si.allJars
      val loader =
        ClasspathUtil.makeLoader(fullCp.map(_.toPath), ConsoleMain.jlineLoader, si, tempDir.toPath)
      runConsole(
        compiler = compiler,
        classpath = cpFiles,
        options = config.scalacOptions,
        loader = loader,
        initialCommands = config.initialCommands,
        cleanupCommands = config.cleanupCommands,
      )(using log)

  private def runConsole(
      compiler: AnalyzingCompiler,
      classpath: Seq[File],
      options: Seq[String],
      loader: ClassLoader,
      initialCommands: String,
      cleanupCommands: String,
  )(using log: Logger): Unit =
    compiler.console(
      classpath.map(x => PlainVirtualFile(x.toPath)),
      MappedFileConverter.empty,
      options,
      initialCommands,
      cleanupCommands,
      log,
    )(
      Some(loader),
      Nil
    )

  def analyzingCompiler(config: ConsoleConfig, si: ScalaInstance): AnalyzingCompiler =
    val bridgeProvider = ZincUtil.constantBridgeProvider(
      si,
      config.bridgeJars.toList match
        case x :: Nil => Paths.get(x)
        case xs       => sys.error(s"expected one bridge jar, but got $xs")
    )
    val classpathOptions = ClasspathOptionsUtil.repl()
    AnalyzingCompiler(
      si,
      bridgeProvider,
      classpathOptions,
      _ => (),
      None
    )

  def scalaInstance(siConfig: ScalaInstanceConfig): ScalaInstance =
    val libraryJars = siConfig.libraryJars.map(Paths.get(_)).sortBy(_.getFileName.toString)
    val allCompilerJars = siConfig.allCompilerJars
      .map(Paths.get(_))
      .sortBy(_.getFileName.toString)
    val jlineJars = allCompilerJars.filter(_.getFileName.toString.contains("jline"))
    val compilerJars =
      allCompilerJars.filterNot(x => libraryJars.contains(x) || jlineJars.contains(x)).distinct
    val extraToolJars0 = siConfig.extraToolJars.map(Paths.get(_)).sortBy(_.getFileName.toString())
    val extraToolJars = extraToolJars0
      .filterNot(jar => libraryJars.contains(jar) || compilerJars.contains(jar))
      .distinct
    val allJars = libraryJars ++ compilerJars ++ extraToolJars
    // Use parent class loader for JLine to avoid conflicts
    val libraryLoader = ClasspathUtil.toLoader(libraryJars, ConsoleMain.jlineLoader)
    val compilerLoader = ClasspathUtil.toLoader(compilerJars, libraryLoader)
    val fullLoader =
      if extraToolJars.isEmpty then compilerLoader
      else ClasspathUtil.toLoader(extraToolJars, compilerLoader)
    new ScalaInstance(
      version = siConfig.scalaVersion,
      loader = fullLoader,
      loaderCompilerOnly = compilerLoader,
      loaderLibraryOnly = libraryLoader,
      libraryJars = libraryJars.map(_.toFile).toArray,
      compilerJars = compilerJars.map(_.toFile).toArray,
      allJars = allJars.map(_.toFile).toArray,
      explicitActual = Some(siConfig.scalaVersion)
    )
end ConsoleMain

object ConsoleMain:
  /** A simple console logger for the forked REPL process. */
  private val consoleLogger: Logger = new Logger:
    override def trace(t: => Throwable): Unit = t.printStackTrace()
    override def success(message: => String): Unit = log(Level.Info, message)
    override def log(level: Level.Value, message: => String): Unit =
      level match
        case Level.Debug => () // Suppress debug messages
        case Level.Info  => scala.Console.out.println(message)
        case Level.Warn  => scala.Console.err.println(s"[warn] $message")
        case Level.Error => scala.Console.err.println(s"[error] $message")

  class FilteredLoader(parent: ClassLoader) extends ClassLoader(parent):
    override final def loadClass(className: String, resolve: Boolean): Class[?] =
      if className.startsWith("org.jline.") || className.startsWith("java.") || className
          .startsWith("javax.") || className.startsWith("sun.")
      then super.loadClass(className, resolve)
      else throw new ClassNotFoundException(className)
    override def getResources(name: String): java.util.Enumeration[URL] = null
    override def getResource(name: String): URL = null
  end FilteredLoader
  lazy val jlineLoader =
    FilteredLoader(classOf[org.jline.terminal.Terminal].getClassLoader)

  def main(args: Array[String]): Unit =
    args.toList match
      case Nil =>
        scala.Console.err.println("ConsoleMain requires a config file argument starting with @")
        sys.exit(1)
      case arg :: Nil if arg.startsWith("@") =>
        import sbt.internal.worker.codec.JsonProtocol.given
        val configFile = arg.drop(1)
        val content = IO.read(File(configFile))
        val json = Parser.parseFromString(content).get
        val config = Converter.fromJson[ConsoleConfig](json).get
        val main = ConsoleMain()
        main.run(config)
      case _ =>
        scala.Console.err.println("ConsoleMain requires exactly one argument: @<config-file>")
        sys.exit(1)
end ConsoleMain
