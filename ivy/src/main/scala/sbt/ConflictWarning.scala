package sbt

	import DependencyFilter._

final case class ConflictWarning(label: String, level: Level.Value, failOnConflict: Boolean)
{
	@deprecated("`filter` is no longer used", "0.13.0")
	val filter: ModuleFilter = (_: ModuleID) => false
	@deprecated("`group` is no longer used", "0.13.0")
	val group: ModuleID => String = ConflictWarning.org
}
private[sbt] final case class ConflictScalaConfig(version: String, organization: String, level: Level.Value)
object ConflictWarning
{
	@deprecated("`group` and `filter` are no longer used.  Use a standard Ivy conflict manager.", "0.13.0")
	def apply(label: String, filter: ModuleFilter, group: ModuleID => String, level: Level.Value, failOnConflict: Boolean): ConflictWarning =
		ConflictWarning(label, level, failOnConflict)

	def disable: ConflictWarning = ConflictWarning("", Level.Debug, false)

	private def org = (_: ModuleID).organization
	private[this] def idString(org: String, name: String) = s"$org:$name"

	def default(label: String): ConflictWarning = ConflictWarning(label, Level.Error, true)

	@deprecated("Warning on evicted modules is no longer done, so this is the same as `default`.  Use a standard Ivy conflict manager.", "0.13.0")
	def strict(label: String): ConflictWarning = ConflictWarning(label, Level.Error, true)

	@deprecated("Conflict warnings now display issues with scala version, and calling is private.", "0.13.5")
	def apply(config: ConflictWarning, report: UpdateReport, log: Logger)
	{
		processCrossVersioned(config, report, log)
	}
	// We don't expose this so we can change the ConflictScalaConfig in the future.
	private[sbt] def apply(config: ConflictWarning, scala: ConflictScalaConfig, report: UpdateReport, log: Logger)
	{
		processCrossVersioned(config, report, log)
		processScalaVersion(config, scala, report, log)
	}
	private[this] def processScalaVersion(config: ConflictWarning, scala: ConflictScalaConfig, report: UpdateReport, log: Logger): Unit = {
		// For now, only warn for the compile scope.
		for(eviction <- report.conflicts.configurations.filter(_.config == "compile").flatMap(_.evictions).find { e =>
			  (e.chosen.organization == scala.organization) &&
			  (e.chosen.revision != scala.version)
		}) {
			log.log(scala.level, 
				s"""|Scala version was updated by one of the library dependencies:
				    | scalaVersion := "${scala.version}", but resolved ${eviction.chosen.organization}:${eviction.chosen.name}:${eviction.chosen.revision}""".stripMargin)
		}
	}
	private[this] def processCrossVersioned(config: ConflictWarning, report: UpdateReport, log: Logger)
	{
		val crossMismatches = crossVersionMismatches(report)
		if(!crossMismatches.isEmpty)
		{
			val pre = s"Modules were resolved with conflicting cross-version suffixes in ${config.label}:\n   "
			val conflictMsgs =
				for( ((org,rawName), fullNames) <- crossMismatches ) yield
				{
					val suffixes = fullNames.map(getCrossSuffix).mkString(", ")
					s"${idString(org,rawName)} $suffixes"
				}
			log.log(config.level, conflictMsgs.mkString(pre, "\n   ", ""))
			if(config.failOnConflict) {
				val summary = crossMismatches.map{ case ((org,raw),_) => idString(org,raw)}.mkString(", ")
				sys.error("Conflicting cross-version suffixes in: " + summary)
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
	private[sbt] def getCrossSuffix(s: String): String = s match {
		case CrossSuffixPattern(_, v) => "_" + v
		case _ => "<none>"
	}

}
