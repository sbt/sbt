/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import sbt._
import sbt.internal.bsp._
import sbt.librarymanagement.Configuration
import sbt.util.InterfaceUtil
import sjsonnew.support.scalajson.unsafe.Converter
import xsbti.compile.{ CompileAnalysis, Inputs }
import xsbti.{ CompileFailed, Problem, Severity }

object BspCompileTask {
  def start(
      targetId: BuildTargetIdentifier,
      project: ProjectRef,
      config: Configuration,
      inputs: Inputs
  ): BspCompileTask = {
    val taskId = TaskId(BuildServerTasks.uniqueId, Vector())
    val targetName = BuildTargetName.fromScope(project.project, config.name)
    val task = BspCompileTask(targetId, targetName, taskId, inputs, System.currentTimeMillis())
    task.notifyStart()
    task
  }
}

case class BspCompileTask private (
    targetId: BuildTargetIdentifier,
    targetName: String,
    id: sbt.internal.bsp.TaskId,
    inputs: Inputs,
    startTimeMillis: Long
) {
  import sbt.internal.bsp.codec.JsonProtocol._

  private[sbt] def notifyStart(): Unit = {
    val message = s"Compiling $targetName"
    val data = Converter.toJsonUnsafe(CompileTask(targetId))
    val params = TaskStartParams(id, startTimeMillis, message, "compile-task", data)
    StandardMain.exchange.notifyEvent("build/taskStart", params)
  }

  private[sbt] def notifySuccess(analysis: CompileAnalysis): Unit = {
    import scala.jdk.CollectionConverters.*
    val endTimeMillis = System.currentTimeMillis()
    val elapsedTimeMillis = endTimeMillis - startTimeMillis
    val sourceInfos = analysis.readSourceInfos().getAllSourceInfos.asScala
    val problems = sourceInfos.values.flatMap(_.getReportedProblems).toSeq
    val isNoOp = InterfaceUtil.toOption(inputs.previousResult.analysis).map(_ == analysis)
    val report = compileReport(problems, elapsedTimeMillis, isNoOp)
    val params = TaskFinishParams(
      id,
      endTimeMillis,
      s"Compiled $targetName",
      StatusCode.Success,
      "compile-report",
      Converter.toJsonUnsafe(report)
    )
    StandardMain.exchange.notifyEvent("build/taskFinish", params)
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
    StandardMain.exchange.notifyEvent("build/taskProgress", params)
  }

  private[sbt] def notifyFailure(cause: Option[CompileFailed]): Unit = {
    val endTimeMillis = System.currentTimeMillis()
    val elapsedTimeMillis = endTimeMillis - startTimeMillis
    val problems = cause.map(_.problems().toSeq).getOrElse(Seq.empty[Problem])
    val report = compileReport(problems, elapsedTimeMillis, None)
    val params = TaskFinishParams(
      id,
      endTimeMillis,
      s"Compiled $targetName",
      StatusCode.Error,
      "compile-report",
      Converter.toJsonUnsafe(report)
    )
    StandardMain.exchange.notifyEvent("build/taskFinish", params)
  }

  private def compileReport(
      problems: Seq[Problem],
      elapsedTimeMillis: Long,
      isNoOp: Option[Boolean]
  ): CompileReport = {
    val countBySeverity = problems.groupBy(_.severity()).view.mapValues(_.size).toMap
    val warnings = countBySeverity.getOrElse(Severity.Warn, 0)
    val errors = countBySeverity.getOrElse(Severity.Error, 0)
    CompileReport(targetId, None, errors, warnings, Some(elapsedTimeMillis.toInt), isNoOp)
  }
}
