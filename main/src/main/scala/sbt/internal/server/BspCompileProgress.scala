/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

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
    underlying.fold(true)(_.advance(current, total, prevPhase, nextPhase))
  }

  override def startUnit(phase: String, unitPath: String): Unit = {
    underlying.foreach(_.startUnit(phase, unitPath))
  }

  override def afterEarlyOutput(success: Boolean): Unit = {
    underlying.foreach(_.afterEarlyOutput(success))
  }
}
