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
import sbt.internal.inc.LoggedReporter
import sbt.internal.util.ManagedLogger
import xsbti.{ Problem, Severity, Position => XPosition }

import scala.collection.mutable

/**
 * Defines a compiler reporter that uses event logging provided by a `ManagedLogger`.
 *
 * @param maximumErrors The maximum errors.
 * @param logger The event managed logger.
 * @param sourcePositionMapper The position mapper.
 */
class BuildServerReporter(
    buildTarget: BuildTargetIdentifier,
    maximumErrors: Int,
    logger: ManagedLogger,
    sourcePositionMapper: XPosition => XPosition = identity[XPosition],
    sources: Seq[File]
) extends LoggedReporter(maximumErrors, logger, sourcePositionMapper) {
  import LoggedReporter.problemFormats._
  import LoggedReporter.problemStringFormats._
  logger.registerStringCodec[Problem]

  import sbt.internal.bsp.codec.JsonProtocol._
  import sbt.internal.inc.JavaInterfaceUtil._

  private lazy val exchange = StandardMain.exchange
  private val problemsByFile = mutable.Map[File, Vector[Diagnostic]]()

  private[sbt] def sendFinalReport(): Unit = {
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

  override def logError(problem: Problem): Unit = {
    publishDiagnostic(problem)

    // console channel can keep using the xsbi.Problem
    logger.errorEvent(problem)
  }

  override def logWarning(problem: Problem): Unit = {
    publishDiagnostic(problem)

    // console channel can keep using the xsbi.Problem
    logger.warnEvent(problem)
  }

  override def logInfo(problem: Problem): Unit = {
    publishDiagnostic(problem)

    // console channel can keep using the xsbi.Problem
    logger.infoEvent(problem)
  }

  private def publishDiagnostic(problem: Problem): Unit = {
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

  private[sbt] def toDiagnosticSeverity(severity: Severity): Long = severity match {
    case Severity.Info  => DiagnosticSeverity.Information
    case Severity.Warn  => DiagnosticSeverity.Warning
    case Severity.Error => DiagnosticSeverity.Error
  }
}
