/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt.test

	import java.io.File
	import sbt.{IO, Path}
	import Path._

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
			"copy" copy (to => rebase(baseDirectory, to)),
			"copy-file" twoArg("Two paths", copyFile _),
			"copy-flat" copy flat
		)

	def apply(command: String, arguments: List[String]): Unit =
		commands.get(command).map( _(arguments) ).getOrElse(scriptError("Unknown command " + command))
	
	def scriptError(message: String): Some[String] = error("Test script error: " + message)
	def spaced[T](l: Seq[T]) = l.mkString(" ")
	def fromStrings(paths: List[String]) = paths.map(fromString)
	def fromString(path: String) = new File(baseDirectory, path)
	def touch(paths: List[String]) = IO.touch(fromStrings(paths))
	def delete(paths: List[String]): Unit = IO.delete(fromStrings(paths))
	/*def sync(from: String, to: String) =
		IO.sync(fromString(from), fromString(to), log)*/
	def copyFile(from: String, to: String): Unit =
		IO.copyFile(fromString(from), fromString(to))
	def makeDirectories(paths: List[String]) =
		IO.createDirectories(fromStrings(paths))

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
			val exitValue = sbt.Process(command :: args, baseDirectory) ! ;
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
				case List(from, to) => action(from, to)
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
		def copy(mapper: File => FileMap): NamedCommand =
			commandName -> {
				case Nil => scriptError("No paths specified for " + commandName + " command.")
				case path :: Nil => scriptError("No destination specified for " + commandName + " command.")
				case paths =>
					val mapped = fromStrings(paths)
					val map = mapper(mapped.last)
					IO.copy( mapped.init x map )
			}
		def wrongArguments(args: List[String]): Some[String] =
			scriptError("Command '" + commandName + "' does not accept arguments (found '" + spaced(args) + "').")
		def wrongArguments(requiredArgs: String, args: List[String]): Some[String] =
			scriptError("Wrong number of arguments to " + commandName + " command.  " + requiredArgs + " required, found: '" + spaced(args) + "'.")
	}
}