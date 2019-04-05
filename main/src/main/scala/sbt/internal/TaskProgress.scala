/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{
  RMap,
  ConsoleOut,
  ConsoleAppender,
  LogOption,
  JLine,
  ManagedLogger,
  ProgressEvent,
  ProgressItem
}
import sbt.util.Level
import scala.concurrent.{ blocking, Future, ExecutionContext }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

/**
 * implements task progress display on the shell.
 */
private[sbt] final class TaskProgress(log: ManagedLogger)
    extends AbstractTaskExecuteProgress
    with ExecuteProgress[Task] {
  private[this] val isReady = new AtomicBoolean(false)
  private[this] val lastTaskCount = new AtomicInteger(0)
  private[this] val isAllCompleted = new AtomicBoolean(false)
  private[this] val isStopped = new AtomicBoolean(false)

  override def initial(): Unit = {
    ConsoleAppender.setTerminalWidth(JLine.terminal.getWidth)
  }

  override def afterReady(task: Task[_]): Unit = {
    isReady.set(true)
  }

  override def afterCompleted[A](task: Task[A], result: Result[A]): Unit = ()

  override def stop(): Unit = {
    isStopped.set(true)
  }

  import ExecutionContext.Implicits._
  Future {
    while (!isReady.get && !isStopped.get) {
      blocking {
        Thread.sleep(500)
      }
    }
    while (!isAllCompleted.get && !isStopped.get) {
      blocking {
        report()
        Thread.sleep(500)
      }
    }
  }

  private[this] val console = ConsoleOut.systemOut
  override def afterAllCompleted(results: RMap[Task, Result]): Unit = {
    // send an empty progress report to clear out the previous report
    val event = ProgressEvent("Info", Vector(), Some(lastTaskCount.get), None, None)
    import sbt.internal.util.codec.JsonProtocol._
    log.logEvent(Level.Info, event)
    isAllCompleted.set(true)
  }
  private[this] val skipReportTasks =
    Set("run", "bgRun", "fgRun", "scala", "console", "consoleProject")
  private[this] def report(): Unit = console.lockObject.synchronized {
    val currentTasks = activeTasks.toVector
    val ltc = lastTaskCount.get
    val currentTasksCount = currentTasks.size
    def report0(): Unit = {
      val event = ProgressEvent("Info", currentTasks map { task =>
        val elapsed = timings.get(task).currentElapsedMicros
        ProgressItem(taskName(task), elapsed)
      }, Some(ltc), None, None)
      import sbt.internal.util.codec.JsonProtocol._
      log.logEvent(Level.Info, event)
    }
    if (containsSkipTasks(currentTasks)) ()
    else report0()
    lastTaskCount.set(currentTasksCount)
  }

  private[this] def containsSkipTasks(tasks: Vector[Task[_]]): Boolean =
    tasks
      .map(t => taskName(t))
      .exists(n => skipReportTasks.exists(m => n.endsWith("/ " + m)))
}

private[sbt] object TaskProgress {
  def isEnabled: Boolean =
    ConsoleAppender.formatEnabledInEnv && sys.props
      .get("sbt.supershell")
      .flatMap(
        str =>
          ConsoleAppender.parseLogOption(str) match {
            case LogOption.Always => Some(true)
            case LogOption.Never  => Some(false)
            case _                => None
          }
      )
      .getOrElse(true)
}
