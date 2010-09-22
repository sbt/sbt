/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

/** Parses commands.  `errorMessage` is the String used when a command is invalid.
* There is no detailed error reporting.
* Input Strings are assumed to be trimmed.*/
class CommandParser(errorMessage: String, defParser: DefinitionParsing) extends CommandParsing
{
	def parseCommand(line: String): Either[String, Command] =
		defParser.parseDefinition(line) match
		{
			case Some(p: ProcessorDefinition) => Right(new DefineProcessor(p))
			case Some(r: RepositoryDefinition) => Right(new DefineRepository(r))
			case None => parseOther(line)
		}
	
	def parseOther(line: String) =
		line match
		{
			case RemoveRegex(label) => Right(new RemoveDefinition(label))
			case HelpCommand | "" => Right(Help)
			case ShowCommand => Right(Show)
			case _ => Left(errorMessage)
		}

	val ShowCommand = "show"
	val HelpCommand = "help"
	val ProcessorCommand = "<label> is <group> <module> <rev>"
	val RepositoryCommand = "<label> at <url>"
	val RemoveCommand = "remove <label>"
	
	val RemoveRegex = """remove\s+(\w+)""".r
}

/** Parses the String representation of definitions.*/
class DefinitionParser extends DefinitionParsing
{
	def parseDefinition(line: String): Option[Definition] =
		line match
		{
			case ProcessorRegex(label, group, name, rev) =>  Some( new ProcessorDefinition(label, group, name, rev) )
			case RepositoryRegex(label, url) =>  Some( new RepositoryDefinition(label, url) )
			case _ => None
		}
		
	val ProcessorRegex = """(\w+)\s+is\s+(\S+)\s+(\S+)\s+(\S+)""".r
	val RepositoryRegex = """(\w+)\s+at\s+(\S+)""".r
}
