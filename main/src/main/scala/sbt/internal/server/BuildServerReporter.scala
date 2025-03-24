/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import sbt.StandardMain
import sbt.internal.bsp.*
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
  Position as XPosition
}

import scala.jdk.CollectionConverters.*
import scala.collection.mutable
import java.nio.file.Path

/**
 * Provides methods for sending success and failure reports and publishing diagnostics.
 */
sealed trait BuildServerReporter extends Reporter {
  private final val sigFilesWritten = "[sig files written]"
  private final val pureExpression = "a pure expression does nothing in statement position"

  protected def isMetaBuild: Boolean

  protected def logger: ManagedLogger

  protected def underlying: Reporter

  protected def publishDiagnostic(problem: Problem): Unit

  def sendSuccessReport(analysis: CompileAnalysis): Unit

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

/**
 * @param bspCompileState what has already been reported in previous compilation.
 * @param sourcePositionMapper a function that maps an xsbti.Position from the generated source
 * (the Scala file) to the input file of the generator (e.g. Twirl file)
 */
final class BuildServerReporterImpl(
    buildTarget: BuildTargetIdentifier,
    bspCompileState: BspCompileState,
    converter: FileConverter,
    sourcePositionMapper: xsbti.Position => xsbti.Position,
    protected override val isMetaBuild: Boolean,
    protected override val logger: ManagedLogger,
    protected override val underlying: Reporter
) extends BuildServerReporter {
  import sbt.internal.bsp.codec.JsonProtocol.*
  import sbt.internal.inc.JavaInterfaceUtil.*

  private lazy val exchange = StandardMain.exchange
  private val problemsByFile = mutable.Map[Path, Vector[Problem]]()

  // sometimes the compiler returns a fake position such as <macro>
  // on Windows, this causes InvalidPathException (see #5994 and #6720)
  private def toDocument(ref: VirtualFileRef): Option[TextDocumentIdentifier] =
    if (ref.id().contains("<")) None
    else Some(TextDocumentIdentifier(converter.toPath(ref).toUri))

  /**
   * Send diagnostics from the compilation to the client.
   * Do not send empty diagnostics if previous ones were also empty ones.
   *
   * @param analysis current compile analysis
   */
  override def sendSuccessReport(analysis: CompileAnalysis): Unit = {
    for ((source, infos) <- analysis.readSourceInfos.getAllSourceInfos.asScala) {
      val problems = infos.getReportedProblems.toVector
      sendReport(source, problems)
    }
    notifyFirstReport()
  }

  override def sendFailureReport(sources: Array[VirtualFile]): Unit = {
    for (source <- sources) {
      val problems = problemsByFile.getOrElse(converter.toPath(source), Vector.empty)
      sendReport(source, problems)
    }
    notifyFirstReport()
  }

  private def sendReport(source: VirtualFileRef, problems: Vector[Problem]): Unit = {
    val oldDocuments = getAndClearPreviousDocuments(source)

    // publish diagnostics if:
    // 1. file had any problems previously: update them with new ones
    // 2. file has fresh problems: report them
    // 3. build project is compiled for the first time: send success report
    if (oldDocuments.nonEmpty || problems.nonEmpty || isFirstReport) {
      val diagsByDocuments = problems
        .flatMap(mapProblemToDiagnostic)
        .groupMap((document, _) => document)((_, diag) => diag)
      updateNewDocuments(source, diagsByDocuments.keys.toVector)

      // send a report for the new documents, the old ones and the source file
      (diagsByDocuments.keySet ++ oldDocuments ++ toDocument(source)).foreach { document =>
        val diags = diagsByDocuments.getOrElse(document, Vector.empty)
        val params = PublishDiagnosticsParams(
          document,
          buildTarget,
          originId = None,
          diags,
          reset = true
        )
        exchange.notifyEvent("build/publishDiagnostics", params)
      }
    }
  }

  protected override def publishDiagnostic(problem: Problem): Unit = {
    for {
      id <- problem.position.sourcePath.toOption
      (document, diagnostic) <- mapProblemToDiagnostic(problem)
    } {
      // Note: We're putting the real path in `fileRef` because the `id` String can take
      // two forms, either a ${something}/relativePath, or the absolute path of the source.
      // But where we query this, we always have _only_ a ${something}/relativePath available.
      // So here we "normalize" to the real path.
      val fileRef = converter.toPath(VirtualFileRef.of(id))
      problemsByFile(fileRef) = problemsByFile.getOrElse(fileRef, Vector.empty) :+ problem

      val params = PublishDiagnosticsParams(
        document,
        buildTarget,
        originId = None,
        Vector(diagnostic),
        reset = false
      )
      exchange.notifyEvent("build/publishDiagnostics", params)
    }
  }

  private def getAndClearPreviousDocuments(source: VirtualFileRef): Seq[TextDocumentIdentifier] =
    bspCompileState.problemsBySourceFiles.getAndUpdate(_ - source).getOrElse(source, Seq.empty)

  private def updateNewDocuments(
      source: VirtualFileRef,
      documents: Vector[TextDocumentIdentifier]
  ): Unit = {
    val _ = bspCompileState.problemsBySourceFiles.updateAndGet(_ + (source -> documents))
  }

  private def isFirstReport: Boolean = bspCompileState.isFirstReport.get
  private def notifyFirstReport(): Unit = {
    val _ = bspCompileState.isFirstReport.set(false)
  }

  /**
   * Map a given problem, in a Scala source file, to a Diagnostic in an user-facing source file.
   * E.g. if the source file is generated from Twirl, the diagnostic will be reported to the Twirl file.
   */
  private def mapProblemToDiagnostic(
      problem: Problem
  ): Option[(TextDocumentIdentifier, Diagnostic)] = {
    val mappedPosition = sourcePositionMapper(problem.position)
    for {
      mappedSource <- mappedPosition.sourcePath.toOption
      document <- toDocument(VirtualFileRef.of(mappedSource))
    } yield {
      val diagnostic = Diagnostic(
        toRange(mappedPosition),
        Option(toDiagnosticSeverity(problem.severity)),
        problem.diagnosticCode().toOption.map(_.code),
        Option("sbt"),
        problem.message
      )
      (document, diagnostic)
    }
  }

  private def toRange(position: xsbti.Position): Range = {
    val startLineOpt = position.startLine.toOption.map(_.toLong - 1)
    val startColumnOpt = position.startColumn.toOption.map(_.toLong)
    val endLineOpt = position.endLine.toOption.map(_.toLong - 1)
    val endColumnOpt = position.endColumn.toOption.map(_.toLong)
    val lineOpt = position.line.toOption.map(_.toLong - 1)
    val columnOpt = position.pointer.toOption.map(_.toLong)

    def toPosition(lineOpt: Option[Long], columnOpt: Option[Long]): Option[Position] =
      lineOpt.map(line => Position(line, columnOpt.getOrElse(0L)))

    val startPos = toPosition(startLineOpt, startColumnOpt)
      .orElse(toPosition(lineOpt, columnOpt))
      .getOrElse(Position(0L, 0L))
    val endPosOpt = toPosition(endLineOpt, endColumnOpt)
    Range(startPos, endPosOpt.getOrElse(startPos))
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
