/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.reporting.{ Diagnostic => ScalaDiagnostic }
import dotty.tools.dotc.reporting.Reporter
// import sbt.StandardMain.exchange
import sbt.internal.ForwardingReporter
import sbt.internal.bsp
import sbt.internal.bsp.{
  BuildTargetIdentifier,
  Diagnostic,
  // DiagnosticSeverity,
  // PublishDiagnosticsParams,
  // Range,
  // TextDocumentIdentifier
}

import java.nio.file.Path
import scala.collection.mutable

class BuildServerEvalReporter(buildTarget: BuildTargetIdentifier, delegate: Reporter)
    extends ForwardingReporter(delegate):
  private val problemsByFile = mutable.Map[Path, Vector[Diagnostic]]()

  override def doReport(dia: ScalaDiagnostic)(using Context): Unit = {
    /*
    for {
      filePath <- if (pos.source.file.exists) Some(Paths.get(pos.source.file.path)) else None
      range <- convertToRange(pos)
    } {
      val bspSeverity = convertToBsp(severity)
      val diagnostic = Diagnostic(range, bspSeverity, None, Option("sbt"), msg)
      problemsByFile(filePath) = problemsByFile.getOrElse(filePath, Vector()) :+ diagnostic
      val params = PublishDiagnosticsParams(
        TextDocumentIdentifier(filePath.toUri),
        buildTarget,
        originId = None,
        Vector(diagnostic),
        reset = false
      )
      exchange.notifyEvent("build/publishDiagnostics", params)
    }
     */
    super.doReport(dia)
  }

  /*
  def finalReport(sourceName: String): Unit = {
    val filePath = Paths.get(sourceName)
    if (Files.exists(filePath)) {
      val diagnostics = problemsByFile.getOrElse(filePath, Vector())
      val params = PublishDiagnosticsParams(
        textDocument = TextDocumentIdentifier(filePath.toUri),
        buildTarget,
        originId = None,
        diagnostics,
        reset = true
      )
      exchange.notifyEvent("build/publishDiagnostics", params)
    }
  }

  private def convertToBsp(severity: Severity): Option[Long] = {
    val result = severity match {
      case Reporter.INFO    => DiagnosticSeverity.Information
      case Reporter.WARNING => DiagnosticSeverity.Warning
      case Reporter.ERROR   => DiagnosticSeverity.Error
    }
    Some(result)
  }

  private def convertToRange(pos: Position): Option[Range] = {
    pos match {
      case _: DefinedPosition =>
        val startLine = pos.source.offsetToLine(pos.start)
        val startChar = pos.start - pos.source.lineToOffset(startLine)
        val endLine = pos.source.offsetToLine(pos.end)
        val endChar = pos.end - pos.source.lineToOffset(endLine)
        Some(
          Range(
            bsp.Position(startLine.toLong, startChar.toLong),
            bsp.Position(endLine.toLong, endChar.toLong)
          )
        )
      case _ => None
    }
  }
   */
end BuildServerEvalReporter
