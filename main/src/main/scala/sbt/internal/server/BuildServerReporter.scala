package sbt.internal.server

import java.io.File

import sbt.StandardMain
import sbt.internal.bsp._
import sbt.internal.inc.ManagedLoggedReporter
import sbt.internal.util.ManagedLogger
import xsbti.compile.CompileAnalysis
import xsbti.{ FileConverter, Problem, Severity, Position => XPosition }

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
    converter: FileConverter
) extends ManagedLoggedReporter(maximumErrors, logger, sourcePositionMapper) {
  import sbt.internal.bsp.codec.JsonProtocol._
  import sbt.internal.inc.JavaInterfaceUtil._

  import scala.collection.JavaConverters._

  lazy val exchange = StandardMain.exchange

  private[sbt] lazy val problemsByFile = new mutable.HashMap[File, mutable.ListBuffer[Problem]]

  override def reset(): Unit = {
    super.reset()
    problemsByFile.clear()
  }

  override def log(problem: Problem): Unit = {
    val pos = problem.position
    pos.sourceFile.toOption foreach { sourceFile: File =>
      problemsByFile.get(sourceFile) match {
        case Some(xs: mutable.ListBuffer[Problem]) => problemsByFile(sourceFile) = xs :+ problem
        case _                                     => problemsByFile(sourceFile) = mutable.ListBuffer(problem)
      }
    }
    super.log(problem)
  }

  override def logError(problem: Problem): Unit = {
    aggregateProblems(problem)

    // console channel can keep using the xsbi.Problem
    super.logError(problem)
  }

  override def logWarning(problem: Problem): Unit = {
    aggregateProblems(problem)

    // console channel can keep using the xsbi.Problem
    super.logWarning(problem)
  }

  override def logInfo(problem: Problem): Unit = {
    aggregateProblems(problem)

    // console channel can keep using the xsbi.Problem
    super.logInfo(problem)
  }

  private[sbt] def resetPrevious(analysis: CompileAnalysis): Unit = {
    val files = analysis.readSourceInfos.getAllSourceInfos.keySet.asScala
    files foreach { file =>
      val params = PublishDiagnosticsParams(
        TextDocumentIdentifier(converter.toPath(file).toUri),
        buildTarget,
        None,
        diagnostics = Vector(),
        reset = true
      )
      exchange.notifyEvent("build/publishDiagnostics", params)
    }
  }

  private[sbt] def aggregateProblems(problem: Problem): Unit = {

    val pos = problem.position
    pos.sourceFile.toOption foreach { sourceFile: File =>
      problemsByFile.get(sourceFile) match {
        case Some(xs: mutable.ListBuffer[Problem]) =>
          val diagnostics = toDiagnostics(xs)
          val params = PublishDiagnosticsParams(
            TextDocumentIdentifier(sourceFile.toURI),
            buildTarget,
            originId = None,
            diagnostics,
            reset = true
          )
          exchange.notifyEvent("build/publishDiagnostics", params)
        case _ =>
      }
    }
  }

  private[sbt] def toDiagnostics(ps: Seq[Problem]): Vector[Diagnostic] = {
    for {
      problem <- ps.toVector
      pos = problem.position
      line0 <- pos.line.toOption.toVector
      pointer0 <- pos.pointer.toOption.toVector
    } yield {
      val line = line0.toLong - 1L
      val pointer = pointer0.toLong
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
