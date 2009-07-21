/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import java.io.File

object CompileOrder extends Enumeration
{
	val Mixed, JavaThenScala, ScalaThenJava = Value
}
private object CompilerCore
{
	def scalaClasspathForJava = FileUtilities.scalaJars.map(_.getAbsolutePath).mkString(File.pathSeparator)
}
sealed abstract class CompilerCore
{
	val ClasspathOptionString = "-classpath"
	val OutputOptionString = "-d"
	
	// Returns false if there were errors, true if there were not.
	protected def process(args: List[String], log: Logger): Boolean
	// Returns false if there were errors, true if there were not.
	protected def processJava(args: List[String], log: Logger): Boolean = true
	protected def scalaClasspathForJava: String
	def actionStartMessage(label: String): String
	def actionNothingToDoMessage: String
	def actionSuccessfulMessage: String
	def actionUnsuccessfulMessage: String
	
	private def classpathString(rawClasspathString: String, includeScala: Boolean) =
		if(includeScala)
			List(rawClasspathString, scalaClasspathForJava).mkString(File.pathSeparator)
		else
			rawClasspathString

	final def apply(label: String, sources: Iterable[Path], classpathString: String, outputDirectory: Path, options: Seq[String], log: Logger): Option[String] =
		apply(label, sources, classpathString, outputDirectory, options, Nil, CompileOrder.Mixed, log)
	final def apply(label: String, sources: Iterable[Path], rawClasspathString: String, outputDirectory: Path, options: Seq[String], javaOptions: Seq[String], order: CompileOrder.Value, log: Logger): Option[String] =
	{
		log.info(actionStartMessage(label))
		def classpathOption(includeScala: Boolean): List[String] =
		{
			val classpath = classpathString(rawClasspathString, includeScala)
			if(classpath.isEmpty)
				Nil
			else
				List(ClasspathOptionString, classpath)
		}
		val outputDir = outputDirectory.asFile
		FileUtilities.createDirectory(outputDir, log) orElse
		{
			def classpathAndOut(javac: Boolean): List[String] = OutputOptionString :: outputDir.getAbsolutePath :: classpathOption(javac)
			
			Control.trapUnit("Compiler error: ", log)
			{
				val sourceList = sources.map(_.asFile.getAbsolutePath).toList
				if(sourceList.isEmpty)
				{
					log.info(actionNothingToDoMessage)
					None
				}
				else
				{
					def filteredSources(extension: String) = sourceList.filter(_.endsWith(extension))
					def compile(label: String, sources: List[String], options: Seq[String], includeScala: Boolean)(process: (List[String], Logger) => Boolean) =
					{
						if(sources.isEmpty)
						{
							log.debug("No "+label+" sources to compile.")
							true
						}
						else
						{
							val arguments = (options ++ classpathAndOut(includeScala) ++ sources).toList
							log.debug(label + " arguments: " + arguments.mkString(" "))
							process(arguments, log)
						}
					}
					def scalaCompile = () =>
					{
						val scalaSourceList = if(order == CompileOrder.Mixed) sourceList else filteredSources(".scala")
						compile("Scala", scalaSourceList, options, false)(process)
					}
					def javaCompile = () =>
					{
						val javaSourceList = filteredSources(".java")
						compile("Java", javaSourceList, javaOptions, true)(processJava)
					}
					
					val (first, second) = if(order == CompileOrder.JavaThenScala) (javaCompile, scalaCompile) else (scalaCompile, javaCompile)
					if(first() && second())
					{
						log.info(actionSuccessfulMessage)
						None
					}
					else
						Some(actionUnsuccessfulMessage)
				}
			}
		}
	}
}

sealed abstract class CompilerBase extends CompilerCore
{
	def actionStartMessage(label: String) = "Compiling " + label + " sources..."
	val actionNothingToDoMessage = "Nothing to compile."
	val actionSuccessfulMessage = "Compilation successful."
	val actionUnsuccessfulMessage = "Compilation unsuccessful."
}
final class ForkCompile(config: ForkScalaCompiler) extends CompilerBase
{
	import java.io.File
	protected def process(arguments: List[String], log: Logger) =
		Fork.scalac(config.javaHome, config.compileJVMOptions, config.scalaJars, arguments, log) == 0
	override protected def processJava(args: List[String], log: Logger) =
		Fork.javac(config.javaHome, args, log) == 0
	override protected def scalaClasspathForJava = config.scalaJars.mkString(File.pathSeparator)
}
object ForkCompile
{
	def apply(config: ForkScalaCompiler, conditional: CompileConditional) =
	{
		import conditional.config.{compileOrder, classpath, javaOptions, label, log, options, outputDirectory, sources}
		// recompile only if any sources were modified after any classes or no classes exist
		val sourcePaths = sources.get
		val newestSource = (0L /: sourcePaths)(_ max _.lastModified)
		val products = (outputDirectory  ** GlobFilter("*.class")).get
		val oldestClass = (java.lang.Long.MAX_VALUE /: products)(_ min _.lastModified)
		if(products.isEmpty || newestSource > oldestClass)
		{
			// full recompile, since we are not doing proper dependency tracking
			FileUtilities.clean(outputDirectory :: Nil, log)
			val compiler = new ForkCompile(config)
			FileUtilities.createDirectory(outputDirectory.asFile, log)
			compiler(label, sourcePaths, Path.makeString(classpath.get), outputDirectory, options, javaOptions, compileOrder, log)
		}
		else
		{
			log.info("Compilation up to date.")
			None
		}
	}
}

// The following code is based on scala.tools.nsc.Main and scala.tools.nsc.ScalaDoc
// Copyright 2005-2008 LAMP/EPFL
// Original author: Martin Odersky

final class Compile(maximumErrors: Int) extends CompilerBase
{
	protected def process(arguments: List[String], log: Logger) =
	{
		import scala.tools.nsc.{CompilerCommand, FatalError, Global, Settings, reporters, util}
		import util.FakePos
		var reporter = new LoggerReporter(maximumErrors, log)
		val settings = new Settings(reporter.error)
		val command = new CompilerCommand(arguments, settings, error, false)
		
		object compiler extends Global(command.settings, reporter)
		if(!reporter.hasErrors)
		{
			val run = new compiler.Run
			run compile command.files
			reporter.printSummary()
		}
		!reporter.hasErrors
	}
	override protected def processJava(args: List[String], log: Logger) =
		(Process("javac", args) ! log) == 0
	protected def scalaClasspathForJava = CompilerCore.scalaClasspathForJava
}
final class Scaladoc(maximumErrors: Int) extends CompilerCore
{
	protected def scalaClasspathForJava = CompilerCore.scalaClasspathForJava
	protected def process(arguments: List[String], log: Logger) =
	{
		import scala.tools.nsc.{doc, CompilerCommand, FatalError, Global, reporters, util}
		import util.FakePos
		val reporter = new LoggerReporter(maximumErrors, log)
		val docSettings: doc.Settings = new doc.Settings(reporter.error)
		val command = new CompilerCommand(arguments, docSettings, error, false)
		object compiler extends Global(command.settings, reporter)
		{
			override val onlyPresentation = true
		}
		if(!reporter.hasErrors)
		{
			val run = new compiler.Run
			run compile command.files
			val generator = new doc.DefaultDocDriver
			{
				lazy val global: compiler.type = compiler
				lazy val settings = docSettings
			}
			generator.process(run.units)
			reporter.printSummary()
		}
		!reporter.hasErrors
	}
	def actionStartMessage(label: String) = "Generating API documentation for " + label + " sources..."
	val actionNothingToDoMessage = "No sources specified."
	val actionSuccessfulMessage = "API documentation generation successful."
	def actionUnsuccessfulMessage = "API documentation generation unsuccessful."
}

// The following code is based on scala.tools.nsc.reporters.{AbstractReporter, ConsoleReporter}
// Copyright 2002-2008 LAMP/EPFL
// Original author: Martin Odersky
final class LoggerReporter(maximumErrors: Int, log: Logger) extends scala.tools.nsc.reporters.Reporter
{
	import scala.tools.nsc.util.{FakePos,Position}
	private val positions = new scala.collection.mutable.HashMap[Position, Severity]
	
	def error(msg: String) { error(FakePos("scalac"), msg) }

	def printSummary()
	{
		if(WARNING.count > 0)
			log.warn(countElementsAsString(WARNING.count, "warning") + " found")
		if(ERROR.count > 0)
			log.error(countElementsAsString(ERROR.count, "error") + " found")
	}
	
	def display(pos: Position, msg: String, severity: Severity)
	{
		severity.count += 1
		if(severity != ERROR || maximumErrors < 0 || severity.count <= maximumErrors)
			print(severityToLevel(severity), pos, msg)
	}
	private def severityToLevel(severity: Severity): Level.Value =
		severity match
		{
			case ERROR => Level.Error
			case WARNING => Level.Warn
			case INFO => Level.Info
		}
	
	private def print(level: Level.Value, posIn: Position, msg: String)
	{
		if(posIn == null)
			log.log(level, msg)
		else
		{
			val pos = posIn.inUltimateSource(posIn.source.getOrElse(null))
			def message =
			{
				val sourcePrefix =
					pos match
					{
						case FakePos(msg) => msg + " "
						case _ => pos.source.map(_.file.path).getOrElse("")
					}
				val lineNumberString = pos.line.map(line => ":" + line + ":").getOrElse(":") + " "
				sourcePrefix + lineNumberString + msg
			}
			log.log(level, message)
			if (!pos.line.isEmpty)
			{
				log.log(level, pos.lineContent.stripLineEnd) // source line with error/warning
				for(column <- pos.column if column > 0) // pointer to the column position of the error/warning
					log.log(level, (" " * (column-1)) + '^')
			}
		}
	}
	override def reset =
	{
		super.reset
		positions.clear
	}

	protected def info0(pos: Position, msg: String, severity: Severity, force: Boolean)
	{
		severity match
		{
			case WARNING | ERROR =>
			{
				if(!testAndLog(pos, severity))
					display(pos, msg, severity)
			}
			case _ => display(pos, msg, severity)
		}
	}
	
	private def testAndLog(pos: Position, severity: Severity): Boolean =
	{
		if(pos == null || pos.offset.isEmpty)
			false
		else if(positions.get(pos).map(_ >= severity).getOrElse(false))
			true
		else
		{
			positions(pos) = severity
			false
		}
	}
}