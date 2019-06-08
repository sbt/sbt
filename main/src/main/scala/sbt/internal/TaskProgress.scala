/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }

import sbt.internal.util._
import sbt.util.Level

import scala.annotation.tailrec

/**
 * implements task progress display on the shell.
 */
private[sbt] final class TaskProgress(log: ManagedLogger)
    extends AbstractTaskExecuteProgress
    with ExecuteProgress[Task] {
  private[this] val lastTaskCount = new AtomicInteger(0)
  private[this] val currentProgressThread = new AtomicReference[Option[ProgressThread]](None)
  private[this] val sleepDuration = SysProp.supersheelSleep
  private[this] final class ProgressThread
      extends Thread("task-progress-report-thread")
      with AutoCloseable {
    private[this] val isClosed = new AtomicBoolean(false)
    setDaemon(true)
    start()
    @tailrec override def run(): Unit = {
      if (!isClosed.get()) {
        try {
          report()
          Thread.sleep(sleepDuration)
        } catch {
          case _: InterruptedException =>
        }
        run()
      }
    }

    override def close(): Unit = {
      isClosed.set(true)
      interrupt()
    }
  }

  override def initial(): Unit = {
    currentProgressThread.get() match {
      case None =>
        currentProgressThread.set(Some(new ProgressThread))
      case _ =>
    }
    ConsoleAppender.setTerminalWidth(JLine.terminal.getWidth)
  }

  override def afterReady(task: Task[_]): Unit = ()

  override def afterCompleted[A](task: Task[A], result: Result[A]): Unit = ()

  override def stop(): Unit = currentProgressThread.getAndSet(None).foreach(_.close())

  override def afterAllCompleted(results: RMap[Task, Result]): Unit = {
    // send an empty progress report to clear out the previous report
    val event = ProgressEvent("Info", Vector(), Some(lastTaskCount.get), None, None)
    import sbt.internal.util.codec.JsonProtocol._
    log.logEvent(Level.Info, event)
    stop()
  }
  private[this] val skipReportTasks =
    Set("run", "bgRun", "fgRun", "scala", "console", "consoleProject")
  private[this] def report(): Unit = {
    val currentTasks = activeTasks.toVector
    val ltc = lastTaskCount.get
    val currentTasksCount = currentTasks.size
    def report0(): Unit = {
      val event = ProgressEvent(
        "Info",
        currentTasks
          .map { task =>
            val elapsed = timings.get(task).currentElapsedMicros
            ProgressItem(taskName(task), elapsed)
          }
          .sortBy(_.name),
        Some(ltc),
        None,
        None
      )
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
