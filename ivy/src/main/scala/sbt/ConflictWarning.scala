package sbt

	import DependencyFilter._

final case class ConflictWarning(label: String, filter: ModuleFilter, group: ModuleID => String, level: Level.Value, failOnConflict: Boolean)
object ConflictWarning
{
	def disable: ConflictWarning = ConflictWarning("", (_: ModuleID) => false, org, Level.Warn, false)

	private[this] def org = (_: ModuleID).organization
	private[this] def idString(org: String, name: String) = s"$org:$name"

	def default(label: String): ConflictWarning = ConflictWarning(label, moduleFilter(organization = GlobFilter(SbtArtifacts.Organization) | GlobFilter(ScalaArtifacts.Organization)), org, Level.Warn, false)

	def strict(label: String): ConflictWarning = ConflictWarning(label, (id: ModuleID) => true, (id: ModuleID) => idString(id.organization, id.name), Level.Error, true)

	def apply(config: ConflictWarning, report: UpdateReport, log: Logger)
	{
		processEvicted(config, report, log)
		processCrossVersioned(config, report, log)
	}
	private[this] def processEvicted(config: ConflictWarning, report: UpdateReport, log: Logger)
	{
		val conflicts = IvyActions.groupedConflicts(config.filter, config.group)(report)
		if(!conflicts.isEmpty)
		{
			val prefix = if(config.failOnConflict) "Incompatible" else "Potentially incompatible"
			val msg = s"$prefix versions of dependencies of ${config.label}:\n   "
			val conflictMsgs =
				for( (label, versions) <- conflicts ) yield
					label + ": " + versions.mkString(", ")
			log.log(config.level, conflictMsgs.mkString(msg, "\n   ", ""))

			if(config.failOnConflict)
				error("Conflicts in " + conflicts.map(_._1).mkString(", ") )
		}
	}
	
	private[this] def processCrossVersioned(config: ConflictWarning, report: UpdateReport, log: Logger)
	{
		val crossMismatches = crossVersionMismatches(report)
		if(!crossMismatches.isEmpty)
		{
			val pre = "Modules were resolved with conflicting cross-version suffixes:\n   "
			val conflictMsgs =
				for( ((org,rawName), fullNames) <- crossMismatches ) yield
				{
					val suffixes = fullNames.map(n => "_" + getCrossSuffix(n)).mkString(", ")
					s"${idString(org,rawName)} $suffixes"
				}
			log.log(config.level, conflictMsgs.mkString(pre, "\n   ", ""))
			if(config.failOnConflict) {
				val summary = crossMismatches.map{ case ((org,raw),_) => idString(org,raw)}.mkString(", ")
				error("Conflicting cross-version suffixes in: " + summary)
			}
		}
	}

	/** Map from (organization, rawName) to set of multiple full names. */
	def crossVersionMismatches(report: UpdateReport): Map[(String,String), Set[String]] =
	{
		val mismatches = report.configurations.flatMap { confReport =>
			groupByRawName(confReport.allModules).mapValues { modules =>
				val differentFullNames = modules.map(_.name).toSet
				if(differentFullNames.size > 1) differentFullNames else Set.empty[String]
			}
		}
		(Map.empty[(String,String),Set[String]] /: mismatches)(merge)
	}
	private[this] def merge[A,B](m: Map[A, Set[B]], b: (A, Set[B])): Map[A, Set[B]] =
		if(b._2.isEmpty) m else 
			m.updated(b._1, m.getOrElse(b._1, Set.empty) ++ b._2)

	private[this] def groupByRawName(ms: Seq[ModuleID]): Map[(String,String), Seq[ModuleID]] =
		ms.groupBy(m => (m.organization, dropCrossSuffix(m.name)))

	private[this] val CrossSuffixPattern = """(.+)_(\d+\.\d+(?:\.\d+)?(?:-.+)?)""".r
	private[this] def dropCrossSuffix(s: String): String = s match {
		case CrossSuffixPattern(raw, _) => raw
		case _ => s
	}
	private[this] def getCrossSuffix(s: String): String = s match {
		case CrossSuffixPattern(_, v) => v
		case _ => ""
	}

}
