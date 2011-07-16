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
			log.log(config.level, "Potentially incompatible versions specified by " + config.label + ":")
		for( (label, versions) <- conflicts )
			log.log(config.level, "   " + label + ": " + versions.mkString(", "))
		if(config.failOnConflict && !conflicts.isEmpty)
			error("Conflicts in " + conflicts.map(_._1).mkString )
	}
}
