package sbt
package internal

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.reporting.ConsoleReporter
import dotty.tools.dotc.reporting.Diagnostic
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.reporting.StoreReporter

abstract class EvalReporter extends Reporter:
  /**
   * Send a final report to clear out the outdated diagnostics.
   * @param sourceName the source path of a build.sbt file
   */
  def finalReport(sourceName: String): Unit

  def log(msg: String): Unit = ()

object EvalReporter:
  def console: EvalReporter = ForwardingReporter(ConsoleReporter())
  def store: EvalReporter = ForwardingReporter(StoreReporter())
end EvalReporter

class ForwardingReporter(delegate: Reporter) extends EvalReporter:
  def doReport(dia: Diagnostic)(using Context): Unit = delegate.doReport(dia)

  def finalReport(sourceName: String): Unit = ()
end ForwardingReporter
