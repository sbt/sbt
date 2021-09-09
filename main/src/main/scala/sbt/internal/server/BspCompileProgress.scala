package sbt.internal.server

import xsbti.compile.CompileProgress

private[sbt] final class BspCompileProgress(
    task: BspCompileTask,
    underlying: Option[CompileProgress]
) extends CompileProgress {
  override def advance(
      current: Int,
      total: Int,
      prevPhase: String,
      nextPhase: String
  ): Boolean = {
    val percentage = current * 100 / total
    // Report percentages every 5% increments
    val shouldReportPercentage = percentage % 5 == 0
    if (shouldReportPercentage) {
      task.notifyProgress(percentage, total)
    }
    underlying.foreach(_.advance(current, total, prevPhase, nextPhase))
    true
  }
}
