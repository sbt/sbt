/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.compiler

import scala.reflect.internal.settings.MutableSettings
import scala.reflect.internal.util.Position
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.{ ConsoleReporter, FilteringReporter }

/**
 * Reporter used to compile *.sbt files that forwards compiler diagnostics to BSP clients
 */
abstract class EvalReporter extends FilteringReporter {

  /**
   * Send a final report to clear out the outdated diagnostics.
   * @param sourceName a *.sbt file
   */
  def finalReport(sourceName: String): Unit
}

object EvalReporter {
  def console(s: Settings): EvalReporter = new ForwardingReporter(new ConsoleReporter(s))
}

class ForwardingReporter(delegate: FilteringReporter) extends EvalReporter {
  def settings: Settings = delegate.settings

  def doReport(pos: Position, msg: String, severity: Severity): Unit =
    delegate.doReport(pos, msg, severity)

  override def filter(pos: Position, msg: String, severity: Severity): Int =
    delegate.filter(pos, msg, severity)

  override def increment(severity: Severity): Unit = delegate.increment(severity)

  override def errorCount: Int = delegate.errorCount
  override def warningCount: Int = delegate.warningCount

  override def hasErrors: Boolean = delegate.hasErrors
  override def hasWarnings: Boolean = delegate.hasWarnings

  override def comment(pos: Position, msg: String): Unit = delegate.comment(pos, msg)

  override def cancelled: Boolean = delegate.cancelled
  override def cancelled_=(b: Boolean): Unit = delegate.cancelled_=(b)

  override def flush(): Unit = delegate.flush()
  override def finish(): Unit = delegate.finish()
  override def reset(): Unit =
    delegate.reset() // super.reset not necessary, own state is never modified

  override def rerunWithDetails(setting: MutableSettings#Setting, name: String): String =
    delegate.rerunWithDetails(setting, name)

  override def finalReport(sourceName: String): Unit = ()
}
