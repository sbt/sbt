/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

/** Executes a parsed command. */
class Execute(manager: Manager, info: Info, log: Logger) extends Executing
{
	def apply(command: Command): Unit =
		command match
		{
			case dr: DefineRepository =>
				manager.defineRepository(dr.repo)
				log.info("Defined new processor repository '" + dr.repo + "'")
			case dp: DefineProcessor =>
				manager.defineProcessor(dp.pdef)
				log.info("Defined new processor '" + dp.pdef + "'")
			case rd: RemoveDefinition =>
				val removed = manager.removeDefinition(rd.label)
				log.info("Removed '" + removed + "'")
			case Help => info.help()
			case Show => info.show()
		}
}