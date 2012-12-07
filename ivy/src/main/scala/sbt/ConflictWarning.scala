package sbt

	import DependencyFilter._

final case class ConflictWarning(label: String, filter: ModuleFilter, group: ModuleID => String, level: Level.Value, failOnConflict: Boolean)
object ConflictWarning
{
	def disable: ConflictWarning = ConflictWarning("", (_: ModuleID) => false, org, Level.Warn, false)

	private[this] def org = (_: ModuleID).organization

	def default(label: String): ConflictWarning = ConflictWarning(label, moduleFilter(organization = GlobFilter(SbtArtifacts.Organization) | GlobFilter(ScalaArtifacts.Organization)), org, Level.Warn, false)

	def strict(label: String): ConflictWarning = ConflictWarning(label, (id: ModuleID) => true, (id: ModuleID) => id.organization + ":" + id.name, Level.Error, true)

	def apply(config: ConflictWarning, report: UpdateReport, log: Logger)
	{
		val conflicts = IvyActions.groupedConflicts(config.filter, config.group)(report)
		if(!conflicts.isEmpty)
		{
			val prefix = if(config.failOnConflict) "Incompatible" else "Potentially incompatible"
			val msg = prefix + " versions of dependencies of " + config.label + ":\n   "
			val conflictMsgs =
				for( (label, versions) <- conflicts ) yield
					label + ": " + versions.mkString(", ")
			log.log(config.level, conflictMsgs.mkString(msg, "\n   ", ""))
		}
		if(config.failOnConflict && !conflicts.isEmpty)
			error("Conflicts in " + conflicts.map(_._1).mkString(", ") )
	}
}
