/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import java.io.File

import sbt.StandardMain
import sbt.internal.bsp._
import sbt.internal.util.ManagedLogger
import xsbti.{ Problem, Reporter, Severity, Position => XPosition }

import scala.collection.mutable

sealed trait BuildServerReporter extends Reporter {
  private final val sigFilesWritten = "[sig files written]"

  protected def logger: ManagedLogger

  protected def underlying: Reporter

  protected def publishDiagnostic(problem: Problem): Unit

  def sendFinalReport(): Unit

  override def reset(): Unit = underlying.reset()

  override def hasErrors: Boolean = underlying.hasErrors

  override def hasWarnings: Boolean = underlying.hasWarnings

  override def printSummary(): Unit = underlying.printSummary()

  override def problems(): Array[Problem] = underlying.problems()

  override def log(problem: Problem): Unit = {
    if (problem.message == sigFilesWritten) {
      logger.debug(sigFilesWritten)
    } else {
      publishDiagnostic(problem)
      underlying.log(problem)
    }
  }

  override def comment(pos: XPosition, msg: String): Unit = underlying.comment(pos, msg)
}

final class BuildServerReporterImpl(
    buildTarget: BuildTargetIdentifier,
    protected override val logger: ManagedLogger,
    protected override val underlying: Reporter,
    sources: Seq[File]
) extends BuildServerReporter {
  import sbt.internal.bsp.codec.JsonProtocol._
  import sbt.internal.inc.JavaInterfaceUtil._

  private lazy val exchange = StandardMain.exchange
  private val problemsByFile = mutable.Map[File, Vector[Diagnostic]]()

  override def sendFinalReport(): Unit = {
    for (source <- sources) {
      val diagnostics = problemsByFile.getOrElse(source, Vector())
      val params = PublishDiagnosticsParams(
        TextDocumentIdentifier(source.toURI),
        buildTarget,
        originId = None,
        diagnostics,
        reset = true
      )
      exchange.notifyEvent("build/publishDiagnostics", params)
    }
  }

  protected override def publishDiagnostic(problem: Problem): Unit = {
    for {
      source <- problem.position.sourceFile.toOption
      diagnostic <- toDiagnostic(problem)
    } {
      problemsByFile(source) = problemsByFile.getOrElse(source, Vector()) :+ diagnostic
      val params = PublishDiagnosticsParams(
        TextDocumentIdentifier(source.toURI),
        buildTarget,
        originId = None,
        Vector(diagnostic),
        reset = false
      )
      exchange.notifyEvent("build/publishDiagnostics", params)
    }
  }

  private def toDiagnostic(problem: Problem): Option[Diagnostic] = {
    val pos = problem.position
    for {
      line <- pos.line.toOption.map(_.toLong - 1L)
      pointer <- pos.pointer.toOption.map(_.toLong)
    } yield {
      val range = (
        pos.startLine.toOption,
        pos.startColumn.toOption,
        pos.endLine.toOption,
        pos.endColumn.toOption
      ) match {
        case (Some(sl), Some(sc), Some(el), Some(ec)) =>
          Range(Position(sl.toLong - 1, sc.toLong), Position(el.toLong - 1, ec.toLong))
        case _ =>
          Range(Position(line, pointer), Position(line, pointer + 1))
      }

      Diagnostic(
        range,
        Option(toDiagnosticSeverity(problem.severity)),
        None,
        Option("sbt"),
        problem.message
      )
    }
  }

  private def toDiagnosticSeverity(severity: Severity): Long = severity match {
    case Severity.Info  => DiagnosticSeverity.Information
    case Severity.Warn  => DiagnosticSeverity.Warning
    case Severity.Error => DiagnosticSeverity.Error
  }
}

final class BuildServerForwarder(
    protected override val logger: ManagedLogger,
    protected override val underlying: Reporter
) extends BuildServerReporter {

  override def sendFinalReport(): Unit = ()

  protected override def publishDiagnostic(problem: Problem): Unit = ()
}
