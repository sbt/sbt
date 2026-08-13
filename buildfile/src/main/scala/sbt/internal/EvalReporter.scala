package sbt
package internal

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interfaces
import dotty.tools.dotc.reporting.ConsoleReporter
import dotty.tools.dotc.reporting.Diagnostic
import dotty.tools.dotc.reporting.HideNonSensicalMessages
import dotty.tools.dotc.reporting.MessageRendering
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.reporting.StoreReporter
import dotty.tools.dotc.reporting.UniqueMessagePositions
import sbt.util.Logger

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
  def logging(log: Logger): EvalReporter = LoggingEvalReporter(log)
end EvalReporter

class ForwardingReporter(delegate: Reporter) extends EvalReporter:
  def doReport(dia: Diagnostic)(using Context): Unit = delegate.doReport(dia)

  def finalReport(sourceName: String): Unit = ()
end ForwardingReporter

/** Forwards diagnostics to an sbt Logger, rendered as the console reporter would. */
class LoggingEvalReporter(logger: Logger)
    extends EvalReporter
    with UniqueMessagePositions
    with HideNonSensicalMessages
    with MessageRendering:
  def doReport(dia: Diagnostic)(using Context): Unit =
    val text = messageAndPos(dia)
    dia.level match
      case interfaces.Diagnostic.ERROR   => logger.error(text)
      case interfaces.Diagnostic.WARNING => logger.warn(text)
      case _                             => logger.info(text)

  def finalReport(sourceName: String): Unit = ()
end LoggingEvalReporter
