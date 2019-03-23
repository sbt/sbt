/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{ RMap, ConsoleOut, ConsoleAppender, LogOption, JLine }
import scala.concurrent.{ blocking, Future, ExecutionContext }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import TaskProgress._

/**
 * implements task progress display on the shell.
 */
private[sbt] final class TaskProgress
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
    isAllCompleted.set(true)
    // completionReport()
  }
  private[this] val skipReportTasks =
    Set("run", "bgRun", "fgRun", "scala", "console", "consoleProject")
  private[this] def report(): Unit = console.lockObject.synchronized {
    val currentTasks = activeTasks.toList
    val ltc = lastTaskCount.get
    val currentTasksCount = currentTasks.size
    def report0(): Unit = {
      console.print(s"$CursorDown1")
      currentTasks foreach { task =>
        val elapsed = timings.get(task).currentElapsedSeconds
        console.println(s"$DeleteLine  | => ${taskName(task)} ${elapsed}s")
      }
      if (ltc > currentTasksCount) deleteConsoleLines(ltc - currentTasksCount)
      else ()
      console.print(cursorUp(math.max(currentTasksCount, ltc) + 1))
    }
    if (containsSkipTasks(currentTasks)) ()
    else report0()
    lastTaskCount.set(currentTasksCount)
  }

  // todo: use logger instead of console
  // private[this] def completionReport(): Unit = console.lockObject.synchronized {
  //   val completedTasks = timings.asScala.toList
  //   val notableTasks = completedTasks
  //     .filter({
  //       case (_, time: Long) => time >= 1000000000L * 10L
  //     })
  //     .sortBy({
  //       case (_, time: Long) => -time
  //     })
  //     .take(5)
  //   def report0(): Unit = {
  //     console.print(s"$CursorDown1")
  //     console.println(s"$DeleteLine  notable completed tasks:")
  //     notableTasks foreach {
  //       case (task, time) =>
  //         val elapsed = time / 1000000000L
  //         console.println(s"$DeleteLine  | => ${taskName(task)} ${elapsed}s")
  //     }
  //   }
  //   if (containsSkipTasks(notableTasks) || notableTasks.isEmpty) ()
  //   else report0()
  // }

  private[this] def containsSkipTasks(tasks: List[Task[_]]): Boolean =
    tasks
      .map(t => taskName(t))
      .exists(n => skipReportTasks.exists(m => n.endsWith("/ " + m)))

  private[this] def deleteConsoleLines(n: Int): Unit = {
    (1 to n) foreach { _ =>
      console.println(s"$DeleteLine")
    }
  }
}

private[sbt] object TaskProgress {
  final val DeleteLine = "\u001B[2K"
  def cursorUp(n: Int): String = s"\u001B[${n}A"
  def cursorDown(n: Int): String = s"\u001B[${n}B"
  final val CursorDown1 = cursorDown(1)

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
