/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

import java.io.File

/** An interface for code that operates on an sbt `Project`.*/
trait Processor extends NotNull
{
	/** Apply this processor's action to the given `project`.
	* The arguments are passed unparsed as a single String `args`.
	* The return value optionally provides additional commands to run, such as 'reload'.
	* Note: `project` is not necessarily the root project.  To get the root project, use `project.rootProject`.*/
	def apply(project: Project, args: String): ProcessorResult
}
/** The result of a Processor run.
* `insertArgs` allows the Processor to insert additional commands to run.
* These commands are run before pending commands.
* 
* For example, consider a Processor bound to 'cleanCompile' that returns
*   `ProcessorResult("clean", "compile")`
* 
* If a user runs:
*   `sbt a cleanCompile b `
* This runs `a`, `cleanCompile`, `clean`, `compile`, and finally `b`.
* Commands are processed as if they were entered at the prompt or from the command line.*/
final class ProcessorResult(insertArgs: String*) extends NotNull
{
	val insertArguments = insertArgs.toList
}

/** Manages the processor and repository definitions.*/
trait Manager extends NotNull
{
	def defineProcessor(pdef: ProcessorDefinition)
	def removeDefinition(label: String): Definition
	def defineRepository(repo: RepositoryDefinition)
	def processor(pdef: ProcessorDefinition): Option[Processor]
	def processorDefinition(label: String): Option[ProcessorDefinition]
	
	def processors: Map[String, ProcessorDefinition]
	def repositories: Map[String, RepositoryDefinition]
}

/** Executes a parsed command. */
trait Executing extends NotNull
{
	def apply(command: Command)
}
/** Prints information about processors. */
trait Info extends NotNull
{
	/** Prints available processors and defined repositories.*/
	def show()
	/** Prints usage of processor management commands.*/
	def help()
}

/** Parses a command String */
trait CommandParsing extends NotNull
{
	/** Parses a command String that has been preprocessed.
	* It should have any prefix (like the * used by Main) removed
	* and whitespace trimmed
	*
	* If parsing is successful, a `Command` instance is returned wrapped in `Right`.
	*  Otherwise, an error message is returned wrapped in `Left`.*/
	def parseCommand(line: String): Either[String, Command]
}
/** Parses a definition `String`.*/
trait DefinitionParsing extends NotNull
{
	/** Parses the given definition `String`.
	* The result is wrapped in `Some` if successful, or `None` if the string is not of the correct form. */
	def parseDefinition(line: String): Option[Definition]
}
/** Handles serializing `Definition`s.*/
trait Persisting extends NotNull
{
	def save(file: File)(definitions: Iterable[Definition])
	def load(file: File): Seq[Definition]
}

sealed trait Definition extends NotNull
{
	def label: String
}
final class ProcessorDefinition(val label: String, val group: String, val module: String, val rev: String) extends Definition
{
	override def toString = Seq(label, "is", group, module, rev).mkString(" ")
	def idString = Seq(group, module, rev).mkString(" ")
	def toModuleID(scalaVersion: String) = ModuleID(group, module + "_" + scalaVersion, rev)
}
// maven-style repositories only right now
final class RepositoryDefinition(val label: String, val url: String) extends Definition
{
	override def toString = Seq(label, "at", url).mkString(" ")
}

/** Data type representing a runnable command related to processor management.*/
sealed trait Command extends NotNull
/** A command to add the given processor definition. */
final class DefineProcessor(val pdef: ProcessorDefinition) extends Command
/** A command to remove the processor or repository definition currently associated with the given `label`.
* If the definition is associated with other labels, those are not affected.*/
final class RemoveDefinition(val label: String) extends Command
/** A command to register the given repository to be used for obtaining `Processor`s. */
final class DefineRepository(val repo: RepositoryDefinition) extends Command
/** A command to show help for processor management command usage. */
object Help extends Command
/** A command to show available processors and repositories.*/
object Show extends Command

/** An exception used when a `Processor` wants to terminate with an error message, but the stack trace is not important.
* If a `cause` is provided, its stack trace is assumed to be important.*/
final class ProcessorException(val message: String, cause: Throwable) extends RuntimeException(message, cause)
{
	def this(message: String) = this(message, null)
}
object ProcessorException
{
	def error(msg: String): Nothing = throw new ProcessorException(msg)
	def error(msg: String, t: Throwable): Nothing = throw new ProcessorException(msg, t)
}
