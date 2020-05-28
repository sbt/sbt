/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import sbt._
import sbt.internal.bsp._
import sbt.librarymanagement.Configuration
import sjsonnew.support.scalajson.unsafe.Converter
import xsbti.compile.CompileResult
import xsbti.{ CompileFailed, Problem, Severity }

import scala.util.control.NonFatal

object BspCompileTask {
  import sbt.internal.bsp.codec.JsonProtocol._

  private lazy val exchange = StandardMain.exchange

  def compute(targetId: BuildTargetIdentifier, project: ProjectRef, config: Configuration)(
      compile: => CompileResult
  ): CompileResult = {
    val task = BspCompileTask(targetId, project, config)
    try {
      notifyStart(task)
      val result = compile
      notifySuccess(task, result)
      result
    } catch {
      case NonFatal(cause) =>
        val compileFailed = cause match {
          case failed: CompileFailed => Some(failed)
          case _                     => None
        }
        notifyFailure(task, compileFailed)
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

  private def notifyStart(task: BspCompileTask): Unit = {
    val message = s"Compiling ${task.targetName}"
    val data = Converter.toJsonUnsafe(CompileTask(task.targetId))
    val params = TaskStartParams(task.id, task.startTimeMillis, message, "compile-task", data)
    exchange.notifyEvent("build/taskStart", params)
  }

  private def notifySuccess(task: BspCompileTask, result: CompileResult): Unit = {
    import collection.JavaConverters._
    val endTimeMillis = System.currentTimeMillis()
    val elapsedTimeMillis = endTimeMillis - task.startTimeMillis
    val problems = result match {
      case compileResult: CompileResult =>
        val sourceInfos = compileResult.analysis().readSourceInfos().getAllSourceInfos.asScala
        sourceInfos.values.flatMap(_.getReportedProblems).toSeq
      case _ => Seq()
    }
    val report = compileReport(problems, task.targetId, elapsedTimeMillis)
    val params = TaskFinishParams(
      task.id,
      endTimeMillis,
      s"Compiled ${task.targetName}",
      StatusCode.Success,
      "compile-report",
      Converter.toJsonUnsafe(report)
    )
    exchange.notifyEvent("build/taskFinish", params)
  }

  private def notifyFailure(task: BspCompileTask, cause: Option[CompileFailed]): Unit = {
    val endTimeMillis = System.currentTimeMillis()
    val elapsedTimeMillis = endTimeMillis - task.startTimeMillis
    val problems = cause.map(_.problems().toSeq).getOrElse(Seq.empty[Problem])
    val report = compileReport(problems, task.targetId, elapsedTimeMillis)
    val params = TaskFinishParams(
      task.id,
      endTimeMillis,
      s"Compiled ${task.targetName}",
      StatusCode.Error,
      "compile-report",
      Converter.toJsonUnsafe(report)
    )
    exchange.notifyEvent("build/taskFinish", params)
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
)
