package sbt
package internal

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.reporting.ConsoleReporter
import dotty.tools.dotc.reporting.Diagnostic
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.reporting.StoreReporter

abstract class EvalReporter extends Reporter

object EvalReporter:
  def console: EvalReporter = ForwardingReporter(ConsoleReporter())
  def store: EvalReporter = ForwardingReporter(StoreReporter())
end EvalReporter

class ForwardingReporter(delegate: Reporter) extends EvalReporter:
  def doReport(dia: Diagnostic)(using Context): Unit = delegate.doReport(dia)
end ForwardingReporter
