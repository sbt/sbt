/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

class InfoImpl(manager: Manager, prefix: String, parser: CommandParser, print: String => Unit) extends Info
{
	def show()
	{
		print("Processors:\n\t" + manager.processors.values.mkString("\n\t"))
		print("\nProcessor repositories:\n\t" + manager.repositories.values.mkString("\n\t"))
	}
	def help()
	{
		import parser.{ShowCommand, HelpCommand, ProcessorCommand, RemoveCommand, RepositoryCommand}
		val usage =
			(HelpCommand -> "Display this help message") ::
			(ShowCommand -> "Display defined processors and repositories") ::
			(ProcessorCommand -> "Define 'label' to be the processor with the given ID") ::
			(RepositoryCommand -> "Add a repository for searching for processors") ::
			(RemoveCommand -> "Undefine the repository or processor with the given 'label'") ::
			Nil
		
		print("Processor management commands:\n   " + (usage.map{ case (c,d) => prefix + "" + c + "    " + d}).mkString("\n   "))
	}
}