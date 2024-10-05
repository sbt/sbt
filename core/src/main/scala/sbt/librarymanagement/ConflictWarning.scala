package sbt.librarymanagement

import sbt.util.{ Logger, Level }

/**
 * Provide warnings for cross version conflicts.
 * A library foo_2.10 and foo_2.11 can potentially be both included on the
 * library dependency graph by mistake, but it won't be caught by eviction.
 */
final case class ConflictWarning(label: String, level: Level.Value, failOnConflict: Boolean) {}
object ConflictWarning {
  def disable: ConflictWarning = ConflictWarning("", Level.Debug, false)

  private[this] def idString(org: String, name: String) = s"$org:$name"

  def default(label: String): ConflictWarning = ConflictWarning(label, Level.Error, true)

  def apply(config: ConflictWarning, report: UpdateReport, log: Logger): Unit = {
    processCrossVersioned(config, report, log)
  }
  private[this] def processCrossVersioned(
      config: ConflictWarning,
      report: UpdateReport,
      log: Logger
  ): Unit = {
    val crossMismatches = crossVersionMismatches(report)
    if (crossMismatches.nonEmpty) {
      val pre =
        s"Modules were resolved with conflicting cross-version suffixes in ${config.label}:\n   "
      val conflictMsgs =
        for (((org, rawName), fullNames) <- crossMismatches) yield {
          val suffixes = fullNames.map(getCrossSuffix).mkString(", ")
          s"${idString(org, rawName)} $suffixes"
        }
      log.log(config.level, conflictMsgs.mkString(pre, "\n   ", ""))
      if (config.failOnConflict) {
        val summary =
          crossMismatches.map { case ((org, raw), _) => idString(org, raw) }.mkString(", ")
        sys.error("Conflicting cross-version suffixes in: " + summary)
      }
    }
  }

  /** Map from (organization, rawName) to set of multiple full names. */
  def crossVersionMismatches(report: UpdateReport): Map[(String, String), Set[String]] = {
    val mismatches = report.configurations.flatMap { confReport =>
      groupByRawName(confReport.allModules).view.mapValues { modules =>
        val differentFullNames = modules.map(_.name).toSet
        if (differentFullNames.size > 1) differentFullNames else Set.empty[String]
      }
    }
    mismatches.foldLeft(Map.empty[(String, String), Set[String]])(merge)
  }
  private[this] def merge[A, B](m: Map[A, Set[B]], b: (A, Set[B])): Map[A, Set[B]] =
    if (b._2.isEmpty) m
    else
      m.updated(b._1, m.getOrElse(b._1, Set.empty) ++ b._2)

  private[this] def groupByRawName(ms: Seq[ModuleID]): Map[(String, String), Seq[ModuleID]] =
    ms.groupBy(m => (m.organization, dropCrossSuffix(m.name)))

  private[this] val CrossSuffixPattern = """(.+)_(\d+(?:\.\d+)?(?:\.\d+)?(?:-.+)?)""".r
  private[this] def dropCrossSuffix(s: String): String = s match {
    case CrossSuffixPattern(raw, _) => raw
    case _                          => s
  }
  private[this] def getCrossSuffix(s: String): String = s match {
    case CrossSuffixPattern(_, v) => "_" + v
    case _                        => "<none>"
  }

}
