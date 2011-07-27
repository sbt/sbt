package sbt

	import DependencyFilter._

final case class ConflictWarning(label: String, filter: ModuleFilter, group: ModuleID => String, level: Level.Value, failOnConflict: Boolean)
object ConflictWarning
{
	def default(label: String): ConflictWarning = ConflictWarning(label, moduleFilter(organization = GlobFilter("org.scala-tools.sbt") | GlobFilter("org.scala-lang")), (_: ModuleID).organization, Level.Warn, false)

	def apply(config: ConflictWarning, report: UpdateReport, log: Logger)
	{
		val conflicts = IvyActions.groupedConflicts(config.filter, config.group)(report)
		if(!conflicts.isEmpty)
		{
			val msg = "Potentially incompatible versions specified by " + config.label + ":"
			val conflictMsgs =
				for( (label, versions) <- conflicts ) yield
					label + ": " + versions.mkString(", ")
			log.log(config.level, msg + conflictMsgs.mkString(msg, "\n   ", ""))
		}
		if(config.failOnConflict && !conflicts.isEmpty)
			error("Conflicts in " + conflicts.map(_._1).mkString )
	}
}
