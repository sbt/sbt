package sbt

import collection.mutable

final class EvictionWarningOptions(
    val configurations: Seq[String],
    val level: Level.Value) {
}
object EvictionWarningOptions {
  def apply(): EvictionWarningOptions =
    new EvictionWarningOptions(Vector("compile"), Level.Warn)
}

object EvictionWarning {
  def apply(options: EvictionWarningOptions, report: UpdateReport, log: Logger): Unit = {
    val evictions = buildEvictions(options, report)
    processEvictions(evictions, log)
  }

  private[sbt] def buildEvictions(options: EvictionWarningOptions, report: UpdateReport): Seq[ModuleDetailReport] = {
    val buffer: mutable.ListBuffer[ModuleDetailReport] = mutable.ListBuffer()
    val confs = report.configurations filter { x => options.configurations contains x.configuration }
    confs flatMap { confReport =>
      confReport.details map { detail =>
        if ((detail.modules exists { _.evicted }) &&
          !(buffer exists { x => (x.organization == detail.organization) && (x.name == detail.name) })) {
          buffer += detail
        }
      }
    }
    buffer.toList.toVector
  }

  private[sbt] def processEvictions(evictions: Seq[ModuleDetailReport], log: Logger): Unit = {
    if (!evictions.isEmpty) {
      log.warn("Some dependencies were evicted:")
      evictions foreach { detail =>
        val revs = detail.modules filter { _.evicted } map { _.module.revision }
        val winner = (detail.modules filterNot { _.evicted } map { _.module.revision }).headOption map { " -> " + _ } getOrElse ""
        log.warn(s"\t* ${detail.organization}:${detail.name} (${revs.mkString(", ")})$winner")
      }
    }
  }
}
