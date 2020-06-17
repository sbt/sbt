/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import sbt.StandardMain
import sbt.internal.bsp._
import sbt.internal.inc.ManagedLoggedReporter
import sbt.internal.util.ManagedLogger
import xsbti.compile.CompileAnalysis
import xsbti.{ FileConverter, Problem, Severity, Position => XPosition }

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
    converter: FileConverter,
    previousAnalysis: Option[CompileAnalysis]
) extends ManagedLoggedReporter(maximumErrors, logger, sourcePositionMapper) {
  import sbt.internal.bsp.codec.JsonProtocol._
  import sbt.internal.inc.JavaInterfaceUtil._
  import scala.collection.JavaConverters._

  lazy val exchange = StandardMain.exchange

  override def reset(): Unit = {
    for {
      analysis <- previousAnalysis
      file <- analysis.readSourceInfos.getAllSourceInfos.keySet().asScala
    } {
      val params = PublishDiagnosticsParams(
        TextDocumentIdentifier(converter.toPath(file).toUri),
        buildTarget,
        None,
        diagnostics = Vector(),
        reset = true
      )
      exchange.notifyEvent("build/publishDiagnostics", params)
    }
    super.reset()
  }

  override def log(problem: Problem): Unit = {
    publishDiagnostic(problem)

    super.log(problem)
  }

  override def logError(problem: Problem): Unit = {
    publishDiagnostic(problem)

    // console channel can keep using the xsbi.Problem
    super.logError(problem)
  }

  override def logWarning(problem: Problem): Unit = {
    publishDiagnostic(problem)

    // console channel can keep using the xsbi.Problem
    super.logWarning(problem)
  }

  override def logInfo(problem: Problem): Unit = {
    publishDiagnostic(problem)

    // console channel can keep using the xsbi.Problem
    super.logInfo(problem)
  }

  private def publishDiagnostic(problem: Problem): Unit = {
    for {
      sourceFile <- problem.position.sourceFile.toOption
      diagnostic <- toDiagnostic(problem)
    } {
      val params = PublishDiagnosticsParams(
        TextDocumentIdentifier(sourceFile.toURI),
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
