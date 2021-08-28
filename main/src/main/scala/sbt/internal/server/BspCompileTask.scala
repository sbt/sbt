/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import sbt._
import sbt.internal.bsp._
import sbt.internal.io.Retry
import sbt.internal.server.BspCompileTask.{ compileReport, exchange }
import sbt.librarymanagement.Configuration
import sjsonnew.support.scalajson.unsafe.Converter
import xsbti.compile.CompileResult
import xsbti.{ CompileFailed, Problem, Severity }

import scala.util.control.NonFatal

object BspCompileTask {
  private lazy val exchange = StandardMain.exchange

  def compute(targetId: BuildTargetIdentifier, project: ProjectRef, config: Configuration)(
      compile: BspCompileTask => CompileResult
  ): CompileResult = {
    val task = BspCompileTask(targetId, project, config)
    try {
      task.notifyStart
      val result = Retry(compile(task))
      task.notifySuccess(result)
      result
    } catch {
      case NonFatal(cause) =>
        val compileFailed = cause match {
          case failed: CompileFailed => Some(failed)
          case _                     => None
        }
        task.notifyFailure(compileFailed)
        throw cause
    }
  }

  private def apply(
      targetId: BuildTargetIdentifier,
      project: ProjectRef,
      config: Configuration
  ): BspCompileTask = {
    val taskId = TaskId(BuildServerTasks.uniqueId, Vector())
    val targetName = BuildTargetName.fromScope(project.project, config.name)
    BspCompileTask(targetId, targetName, taskId, System.currentTimeMillis())
  }

  private def compileReport(
      problems: Seq[Problem],
      targetId: BuildTargetIdentifier,
      elapsedTimeMillis: Long
  ): CompileReport = {
    val countBySeverity = problems.groupBy(_.severity()).mapValues(_.size)
    val warnings = countBySeverity.getOrElse(Severity.Warn, 0)
    val errors = countBySeverity.getOrElse(Severity.Error, 0)
    CompileReport(targetId, None, errors, warnings, Some(elapsedTimeMillis.toInt))
  }
}

case class BspCompileTask private (
    targetId: BuildTargetIdentifier,
    targetName: String,
    id: TaskId,
    startTimeMillis: Long
) {
  import sbt.internal.bsp.codec.JsonProtocol._

  private[sbt] def notifyStart(): Unit = {
    val message = s"Compiling $targetName"
    val data = Converter.toJsonUnsafe(CompileTask(targetId))
    val params = TaskStartParams(id, startTimeMillis, message, "compile-task", data)
    exchange.notifyEvent("build/taskStart", params)
  }

  private[sbt] def notifySuccess(result: CompileResult): Unit = {
    import collection.JavaConverters._
    val endTimeMillis = System.currentTimeMillis()
    val elapsedTimeMillis = endTimeMillis - startTimeMillis
    val problems = result match {
      case compileResult: CompileResult =>
        val sourceInfos = compileResult.analysis().readSourceInfos().getAllSourceInfos.asScala
        sourceInfos.values.flatMap(_.getReportedProblems).toSeq
      case _ => Seq()
    }
    val report = compileReport(problems, targetId, elapsedTimeMillis)
    val params = TaskFinishParams(
      id,
      endTimeMillis,
      s"Compiled $targetName",
      StatusCode.Success,
      "compile-report",
      Converter.toJsonUnsafe(report)
    )
    exchange.notifyEvent("build/taskFinish", params)
  }

  private[sbt] def notifyProgress(percentage: Int, total: Int): Unit = {
    val data = Converter.toJsonUnsafe(CompileTask(targetId))
    val message = s"Compiling $targetName ($percentage%)"
    val currentMillis = System.currentTimeMillis()
    val params = TaskProgressParams(
      id,
      Some(currentMillis),
      Some(message),
      Some(total.toLong),
      Some(percentage.toLong),
      None,
      Some("compile-progress"),
      Some(data)
    )
    exchange.notifyEvent("build/taskProgress", params)
  }

  private[sbt] def notifyFailure(cause: Option[CompileFailed]): Unit = {
    val endTimeMillis = System.currentTimeMillis()
    val elapsedTimeMillis = endTimeMillis - startTimeMillis
    val problems = cause.map(_.problems().toSeq).getOrElse(Seq.empty[Problem])
    val report = compileReport(problems, targetId, elapsedTimeMillis)
    val params = TaskFinishParams(
      id,
      endTimeMillis,
      s"Compiled $targetName",
      StatusCode.Error,
      "compile-report",
      Converter.toJsonUnsafe(report)
    )
    exchange.notifyEvent("build/taskFinish", params)
  }
}
