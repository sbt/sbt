/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.reporting.{ Diagnostic as ScalaDiagnostic }
import dotty.tools.dotc.util.SourcePosition
import sbt.StandardMain.exchange
import sbt.internal.EvalReporter
import sbt.internal.bsp
import sbt.internal.bsp.BuildTargetIdentifier
import sbt.internal.bsp.Diagnostic
import sbt.internal.bsp.DiagnosticSeverity
import sbt.internal.bsp.PublishDiagnosticsParams
import sbt.internal.bsp.Range
import sbt.internal.bsp.TextDocumentIdentifier
import sbt.internal.bsp.codec.JsonProtocol.*

import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable

class BuildServerEvalReporter(buildTarget: BuildTargetIdentifier, delegate: Reporter)
    extends EvalReporter:
  private val problemsByFile = mutable.Map[Path, Vector[Diagnostic]]()

  override def doReport(dia: ScalaDiagnostic)(using Context): Unit = {
    if (dia.pos.exists) {
      val filePath = Paths.get(dia.pos.source.file.path)
      val range = convertToRange(dia.pos)
      val bspSeverity = convertToBsp(dia.level)
      val diagnostic = Diagnostic(range, bspSeverity, None, Option("sbt"), dia.msg.message)
      problemsByFile(filePath) = problemsByFile.getOrElse(filePath, Vector()) :+ diagnostic
      val params = PublishDiagnosticsParams(
        TextDocumentIdentifier(filePath.toUri),
        buildTarget,
        originId = None,
        Vector(diagnostic),
        reset = false
      )
      exchange.notifyEvent("build/publishDiagnostics", params)
      delegate.doReport(dia)
    }
  }

  override def finalReport(sourceName: String): Unit = {
    val filePath = Paths.get(sourceName)
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

  private def convertToBsp(severity: Int): Option[Long] = {
    val result = severity match {
      case dotty.tools.dotc.interfaces.Diagnostic.INFO    => DiagnosticSeverity.Information
      case dotty.tools.dotc.interfaces.Diagnostic.WARNING => DiagnosticSeverity.Warning
      case dotty.tools.dotc.interfaces.Diagnostic.ERROR   => DiagnosticSeverity.Error
    }
    Some(result)
  }

  private def convertToRange(pos: SourcePosition): Range = {
    val startLine = pos.source.offsetToLine(pos.start)
    val startChar = pos.start - pos.source.lineToOffset(startLine)
    val endLine = pos.source.offsetToLine(pos.end)
    val endChar = pos.end - pos.source.lineToOffset(endLine)
    Range(
      bsp.Position(startLine.toLong, startChar.toLong),
      bsp.Position(endLine.toLong, endChar.toLong)
    )
  }
end BuildServerEvalReporter
