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

/**
Provides methods for sending success and failure reports and publishing diagnostics.
 */
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

/**

An implementation of the BuildServerReporter for communicating with the Build Server.
Sends diagnostic messages to the client, handling success and failure cases.
@param buildTarget the identifier of the build target
@param bspCompileState state representing what has already been reported in previous compilation.
@param converter a file converter for converting between VirtualFileRef and Path
@param sourcePositionMapper a function to map an xsbti.Position from the generated file (the Scala file) to the source file of the generator (e.g. Twirl file)
@param isMetaBuild a flag indicating if this is a meta build
@param logger a ManagedLogger for logging messages
@param underlying the underlying reporter instance which reports to the sbt shell or native clients
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
  import sbt.internal.bsp.codec.JsonProtocol._
  import sbt.internal.inc.JavaInterfaceUtil._

  private lazy val exchange = StandardMain.exchange
  //keeps track of problems in a given file by mapping its VirtualFileRef to a Vector of problems.
  //N.B : In case of a source generator file (Twirl), the given file is the generated one (Scala).
  private val problemsByFile = mutable.Map[VirtualFileRef, Vector[Problem]]()

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
      sourcePath <- toSafePath(source)
    } {
      // clear problems for current file
      val oldDocuments =
        bspCompileState.problemsBySourceFiles.getAndUpdate(_ - source).getOrElse(source, Seq.empty)

      val problems = infos.getReportedProblems.toVector

      // publish diagnostics if:
      // 1. file had any problems previously - we might want to update them with new ones
      // 2. file has fresh problems - we might want to update old ones
      // 3. build project is compiled first time - shouldReportAllProblems is set
      val shouldPublish = oldDocuments.nonEmpty || problems.nonEmpty || shouldReportAllProblems

      if (shouldPublish) {
        // Group diagnostics by document
        val diagsByDocuments = problems
          .flatMap(mapProblemToDiagnostic)
          .groupBy { case (document, _) => document }
          .mapValues(_.map { case (_, diag) => diag })

        //Get a set of these diagnostics to remove duplicates
        val newDocuments = diagsByDocuments.keySet

        bspCompileState.problemsBySourceFiles.updateAndGet(_ + (source -> newDocuments.toVector))

        val sourceDocument = TextDocumentIdentifier(sourcePath.toUri)
        val allDocuments = (newDocuments ++ oldDocuments + sourceDocument)
        // Iterate through both new and old documents, sending diagnostics for each
        allDocuments.foreach { document =>
          val diags: Vector[Diagnostic] = diagsByDocuments
            .getOrElse(document, Vector.empty)
          val params = PublishDiagnosticsParams(
            document,
            buildTarget,
            originId = None,
            diags,
            reset = true
          )
          // Notify the client with the diagnostics
          exchange.notifyEvent("build/publishDiagnostics", params)
        }
      }
    }
  }

  /**
   *This method sends a failure report to the client when the compilation fails. It takes an array of virtual files
   *as the input parameter and processes the reported problems for each source.
  @param sources an array of virtual files representing the source files
   */
  override def sendFailureReport(sources: Array[VirtualFile]): Unit = {
    val shouldReportAllProblems = !bspCompileState.compiledAtLeastOnce.get
    // Iterate through all source files
    for {
      source <- sources
    } {
      // Get the problems associated with the current source file
      val problems = problemsByFile.getOrElse(source, Vector.empty)

      val oldDocuments =
        bspCompileState.problemsBySourceFiles.getAndUpdate(_ - source).getOrElse(source, Seq.empty)
      // Determine if diagnostics should be published
      // 1. The file had problems previously - we might want to update them with new ones
      // 2. The file has fresh problems - we might want to update old ones
      // 3. The build project is compiled for the first time - shouldReportAllProblems is set
      val shouldPublish = oldDocuments.nonEmpty || problems.nonEmpty || shouldReportAllProblems

      if (shouldPublish) {
        // Group diagnostics by document
        val diagsByDocuments = problems
          .flatMap(mapProblemToDiagnostic)
          .groupBy { case (document, _) => document }
          .mapValues(_.map { case (_, diag) => diag })

        val newDocuments = diagsByDocuments.keySet

        bspCompileState.problemsBySourceFiles.updateAndGet(_ + (source -> newDocuments.toVector))

        // Iterate through both new and old documents, sending diagnostics for each
        (newDocuments ++ oldDocuments).foreach { document =>
          val diags: Vector[Diagnostic] = diagsByDocuments
            .getOrElse(document, Vector.empty)
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
  }

  protected override def publishDiagnostic(problem: Problem): Unit = {
    for {
      id <- problem.position.sourcePath.toOption
      // mapProblemToDiagnostic  maps the position in the Scala source file back to the source of the generator that generated this Scala file.
      (document, diagnostic) <- mapProblemToDiagnostic(problem)
    } {
      val fileRef = VirtualFileRef.of(id)
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

  /**

   * This function maps a given problem  in a Scala source file  to a diagnostic in the source of the generator that generated this Scala
   * or the the file itself in case it was not generated.
   * @param problem the problem to be converted into a diagnostic
   */
  private def mapProblemToDiagnostic(
      problem: Problem
  ): Option[(TextDocumentIdentifier, Diagnostic)] = {
    // Map the position of the problem  from the generated file to the origin , this way we send the original position of the problem instead of the generated one
    val mappedPosition = sourcePositionMapper(problem.position)
    for {
      id <- mappedPosition.sourcePath.toOption
      path <- toSafePath(VirtualFileRef.of(id))
    } yield {
      val diagnostic = Diagnostic(
        toRange(mappedPosition),
        Option(toDiagnosticSeverity(problem.severity)),
        problem.diagnosticCode().toOption.map(_.code),
        Option("sbt"),
        problem.message
      )
      (TextDocumentIdentifier(path.toUri), diagnostic)
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
