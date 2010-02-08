/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt.test

import java.io.File
import xsbt.{FileMapper, FileUtilities, Paths}

class FileCommands(baseDirectory: File) extends BasicStatementHandler
{
	lazy val commands = commandMap
	def commandMap = 
		Map(
			"touch" nonEmpty touch _,
			"delete" nonEmpty delete _,
			"exists" nonEmpty exists _,
			"mkdir" nonEmpty makeDirectories _,
			"absent" nonEmpty absent _,
//			"sync" twoArg("Two directory paths", sync _),
			"newer" twoArg("Two paths", newer _),
			"pause" noArg { readLine("Press enter to continue. "); println() },
			"sleep" oneArg("Time in milliseconds",  time => Thread.sleep(time.toLong) ),
			"exec" nonEmpty(execute _ ),
			"copy" copy (to => FileMapper.rebase(baseDirectory, to)),
			"copy-file" twoArg("Two paths", copyFile _),
			"copy-flat" copy (FileMapper.flat)
		)

	def apply(command: String, arguments: List[String]): Unit =
		commands.get(command).map( _(arguments) ).getOrElse(scriptError("Unknown command " + command))
	
	def scriptError(message: String): Some[String] = error("Test script error: " + message)
	def spaced[T](l: Seq[T]) = l.mkString(" ")
	def fromStrings(paths: List[String]) = paths.map(fromString)
	def fromString(path: String) =
	{
		import Paths._
		baseDirectory / path
	}
	def touch(paths: List[String]) = FileUtilities.touch(fromStrings(paths))
	def delete(paths: List[String]): Unit = FileUtilities.delete(fromStrings(paths))
	/*def sync(from: String, to: String) =
		FileUtilities.sync(fromString(from), fromString(to), log)*/
	def copyFile(from: String, to: String): Unit =
		FileUtilities.copyFile(fromString(from), fromString(to))
	def makeDirectories(paths: List[String]) =
		 FileUtilities.createDirectories(fromStrings(paths))
	def newer(a: String, b: String) =
	{
		val pathA = fromString(a)
		val pathB = fromString(b)
		pathA.exists && (!pathB.exists || pathA.lastModified > pathB.lastModified)
	}
	def exists(paths: List[String])
	{
		val notPresent = fromStrings(paths).filter(!_.exists)
		if(notPresent.length > 0)
			scriptError("File(s) did not exist: " + notPresent.mkString("[ ", " , ", " ]"))
	}
	def absent(paths: List[String])
	{
		val present = fromStrings(paths).filter(_.exists)
		if(present.length > 0)
			scriptError("File(s) existed: " + present.mkString("[ ", " , ", " ]"))
	}
	def execute(command: List[String]): Unit = execute0(command.head, command.tail)
	def execute0(command: String, args: List[String])
	{
		if(command.trim.isEmpty)
			scriptError("Command was empty.")
		else
		{
			val builder = new java.lang.ProcessBuilder((command :: args).toArray :  _*).directory(baseDirectory)
			val exitValue = ( Process(builder) ! )
			if(exitValue != 0)
				error("Nonzero exit value (" + exitValue + ")")
		}
	}

	// these are for readability of the command list
	implicit def commandBuilder(s: String): CommandBuilder = new CommandBuilder(s)
	final class CommandBuilder(commandName: String) extends NotNull
	{
		type NamedCommand = (String, List[String] => Unit)
		def nonEmpty(action: List[String] => Unit): NamedCommand =
			commandName -> { paths =>
				if(paths.isEmpty)
					scriptError("No arguments specified for " + commandName + " command.")
				else
					action(paths)
			}
		def twoArg(requiredArgs: String, action: (String, String) => Unit): NamedCommand =
			commandName -> {
				case List(from, to) => copyFile(from, to)
				case other => wrongArguments(requiredArgs, other)
			}
		def noArg(action: => Unit): NamedCommand =
			commandName -> {
				case Nil => action
				case other => wrongArguments(other)
			}
		def oneArg(requiredArgs: String, action: String => Unit): NamedCommand =
			commandName -> {
				case List(single) => action(single)
				case other => wrongArguments(requiredArgs, other)
			}
		def copy(mapper: File => FileMapper): NamedCommand =
			commandName -> {
				case Nil => scriptError("No paths specified for " + commandName + " command.")
				case path :: Nil => scriptError("No destination specified for " + commandName + " command.")
				case paths =>
					val mapped = fromStrings(paths).toArray
					val last = mapped.length - 1
					val map = mapper(mapped(last))
					import Paths._
					FileUtilities.copy( mapped.take(last) x map )
			}
		def wrongArguments(args: List[String]): Some[String] =
			scriptError("Command '" + commandName + "' does not accept arguments (found '" + spaced(args) + "').")
		def wrongArguments(requiredArgs: String, args: List[String]): Some[String] =
			scriptError("Wrong number of arguments to " + commandName + " command.  " + requiredArgs + " required, found: '" + spaced(args) + "'.")
	}
}