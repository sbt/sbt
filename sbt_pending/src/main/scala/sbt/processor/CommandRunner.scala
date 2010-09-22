/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

/** Parses and executes a command (connects a parser to a runner). */
class CommandRunner(parser: CommandParsing, execute: Executing)
{
	def apply(processorCommand: String): Unit =
		parser.parseCommand(processorCommand) match
		{
			case Left(err) => throw new ProcessorException(err)
			case Right(command) => execute(command)
		}
}
object CommandRunner
{
	/** Convenience method for constructing a CommandRunner with the minimal information required.*/
	def apply(manager: Manager, defParser: DefinitionParser, prefix: String, log: Logger): CommandRunner =
	{
		val parser = new CommandParser(defaultErrorMessage(prefix), defParser)
		val info = new InfoImpl(manager, prefix, parser, System.out.println)
		val execute = new Execute(manager, info, log)
		new CommandRunner(parser, execute)
	}
	def defaultErrorMessage(prefix: String) =
		"Invalid processor command.  Run " + prefix + "help to see valid commands."
}