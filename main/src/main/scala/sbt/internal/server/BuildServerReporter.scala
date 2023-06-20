/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import java.nio.file.Path

import sbt.StandardMain
import sbt.internal.bsp._
import sbt.internal.util.ManagedLogger
import sbt.internal.server.BuildServerProtocol.BspCompileState
import xsbti.compile.CompileAnalysis
import xsbti.{
  FileConverter,
  Problem,
  Reporter,
  Severity,
  VirtualFile,
  VirtualFileRef,
  Position => XPosition
}

import scala.collection.JavaConverters._
import scala.collection.mutable

sealed trait BuildServerReporter extends Reporter {
  private final val sigFilesWritten = "[sig files written]"
  private final val pureExpression = "a pure expression does nothing in statement position"

  protected def isMetaBuild: Boolean

  protected def logger: ManagedLogger

  protected def underlying: Reporter

  protected def publishDiagnostic(problem: Problem): Unit

  def sendSuccessReport(
      analysis: CompileAnalysis,
  ): Unit

  def sendFailureReport(sources: Array[VirtualFile]): Unit

  override def reset(): Unit = underlying.reset()

  override def hasErrors: Boolean = underlying.hasErrors

  override def hasWarnings: Boolean = underlying.hasWarnings

  override def printSummary(): Unit = underlying.printSummary()

  override def problems(): Array[Problem] = underlying.problems()

  override def log(problem: Problem): Unit = {
    if (problem.message == sigFilesWritten) {
      logger.debug(sigFilesWritten)
    } else if (isMetaBuild && problem.message.startsWith(pureExpression)) {
      // work around https://github.com/scala/bug/issues/12112 by ignoring it in the reporter
      logger.debug(problem.message)
    } else {
      publishDiagnostic(problem)
      underlying.log(problem)
    }
  }

  override def comment(pos: XPosition, msg: String): Unit = underlying.comment(pos, msg)
}

final class BuildServerReporterImpl(
    buildTarget: BuildTargetIdentifier,
    bspCompileState: BspCompileState,
    converter: FileConverter,
    protected override val isMetaBuild: Boolean,
    protected override val logger: ManagedLogger,
    protected override val underlying: Reporter
) extends BuildServerReporter {
  import sbt.internal.bsp.codec.JsonProtocol._
  import sbt.internal.inc.JavaInterfaceUtil._

  private lazy val exchange = StandardMain.exchange
  private val problemsByFile = mutable.Map[Path, Vector[Diagnostic]]()

  // sometimes the compiler returns a fake position such as <macro>
  // on Windows, this causes InvalidPathException (see #5994 and #6720)
  private def toSafePath(ref: VirtualFileRef): Option[Path] =
    if (ref.id().contains("<")) None
    else Some(converter.toPath(ref))

  /**
   * Send diagnostics from the compilation to the client.
   * Do not send empty diagnostics if previous ones were also empty ones.
   *
   * @param analysis current compile analysis
   */
  override def sendSuccessReport(
      analysis: CompileAnalysis,
  ): Unit = {
    val shouldReportAllProblems = !bspCompileState.compiledAtLeastOnce.getAndSet(true)
    for {
      (source, infos) <- analysis.readSourceInfos.getAllSourceInfos.asScala
      filePath <- toSafePath(source)
    } {
      // clear problems for current file
      val hadProblems = bspCompileState.hasAnyProblems.remove(filePath)

      val reportedProblems = infos.getReportedProblems.toVector
      val diagnostics = reportedProblems.map(toDiagnostic)

      // publish diagnostics if:
      // 1. file had any problems previously - we might want to update them with new ones
      // 2. file has fresh problems - we might want to update old ones
      // 3. build project is compiled first time - shouldReportAllProblems is set
      val shouldPublish = hadProblems || diagnostics.nonEmpty || shouldReportAllProblems

      // file can have some warnings
      if (diagnostics.nonEmpty) {
        bspCompileState.hasAnyProblems.add(filePath)
      }

      if (shouldPublish) {
        val params = PublishDiagnosticsParams(
          textDocument = TextDocumentIdentifier(filePath.toUri),
          buildTarget,
          originId = None,
          diagnostics.toVector,
          reset = true
        )
        exchange.notifyEvent("build/publishDiagnostics", params)
      }
    }
  }
  override def sendFailureReport(sources: Array[VirtualFile]): Unit = {
    val shouldReportAllProblems = !bspCompileState.compiledAtLeastOnce.get
    for {
      source <- sources
      filePath <- toSafePath(source)
    } {
      val diagnostics = problemsByFile.getOrElse(filePath, Vector.empty)

      val hadProblems = bspCompileState.hasAnyProblems.remove(filePath)
      val shouldPublish = hadProblems || diagnostics.nonEmpty || shouldReportAllProblems

      // mark file as file with problems
      if (diagnostics.nonEmpty) {
        bspCompileState.hasAnyProblems.add(filePath)
      }

      if (shouldPublish) {
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
  }

  protected override def publishDiagnostic(problem: Problem): Unit = {
    for {
      id <- problem.position.sourcePath.toOption
      filePath <- toSafePath(VirtualFileRef.of(id))
    } {
      val diagnostic = toDiagnostic(problem)
      problemsByFile(filePath) = problemsByFile.getOrElse(filePath, Vector.empty) :+ diagnostic
      val params = PublishDiagnosticsParams(
        TextDocumentIdentifier(filePath.toUri),
        buildTarget,
        originId = None,
        Vector(diagnostic),
        reset = false
      )
      exchange.notifyEvent("build/publishDiagnostics", params)
    }
  }

  private def toRange(pos: XPosition): Range = {
    val startLineOpt = pos.startLine.toOption.map(_.toLong - 1)
    val startColumnOpt = pos.startColumn.toOption.map(_.toLong)
    val endLineOpt = pos.endLine.toOption.map(_.toLong - 1)
    val endColumnOpt = pos.endColumn.toOption.map(_.toLong)
    val lineOpt = pos.line.toOption.map(_.toLong - 1)
    val columnOpt = pos.pointer.toOption.map(_.toLong)

    def toPosition(lineOpt: Option[Long], columnOpt: Option[Long]): Option[Position] =
      lineOpt.map(line => Position(line, columnOpt.getOrElse(0L)))

    val startPos = toPosition(startLineOpt, startColumnOpt)
      .orElse(toPosition(lineOpt, columnOpt))
      .getOrElse(Position(0L, 0L))
    val endPosOpt = toPosition(endLineOpt, endColumnOpt)
    Range(startPos, endPosOpt.getOrElse(startPos))
  }

  private def toDiagnostic(problem: Problem): Diagnostic = {
    val actions0 = problem.actions().asScala.toVector
    val data =
      if (actions0.isEmpty) None
      else
        Some(
          ScalaDiagnostic(
            actions = actions0.map { a =>
              ScalaAction(
                title = a.title,
                description = a.description.toOption,
                edit = Some(
                  ScalaWorkspaceEdit(
                    changes = a.edit.changes().asScala.toVector.map { edit =>
                      ScalaTextEdit(
                        range = toRange(edit.position),
                        newText = edit.newText,
                      )
                    }
                  )
                ),
              )
            }
          )
        )
    Diagnostic(
      range = toRange(problem.position),
      severity = Option(toDiagnosticSeverity(problem.severity)),
      code = problem.diagnosticCode().toOption.map(_.code),
      source = Option("sbt"),
      message = problem.message,
      relatedInformation = Vector.empty,
      dataKind = data.map { _ =>
        "scala"
      },
      data = data,
    )
  }

  private def toDiagnosticSeverity(severity: Severity): Long = severity match {
    case Severity.Info  => DiagnosticSeverity.Information
    case Severity.Warn  => DiagnosticSeverity.Warning
    case Severity.Error => DiagnosticSeverity.Error
  }
}

final class BuildServerForwarder(
    protected override val isMetaBuild: Boolean,
    protected override val logger: ManagedLogger,
    protected override val underlying: Reporter
) extends BuildServerReporter {

  override def sendSuccessReport(
      analysis: CompileAnalysis,
  ): Unit = ()

  override def sendFailureReport(sources: Array[VirtualFile]): Unit = ()

  protected override def publishDiagnostic(problem: Problem): Unit = ()
}
