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
  private[this] val sleepDuration = SysProp.supershellSleep
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

  override def initial(): Unit = ConsoleAppender.setTerminalWidth(JLine.terminal.getWidth)

  override def beforeWork(task: Task[_]): Unit = {
    super.beforeWork(task)
    if (containsSkipTasks(Vector(task)) || lastTaskCount.get == 0) report()
  }
  override def afterReady(task: Task[_]): Unit = ()

  override def afterCompleted[A](task: Task[A], result: Result[A]): Unit = ()

  override def stop(): Unit = currentProgressThread.getAndSet(None).foreach(_.close())

  override def afterAllCompleted(results: RMap[Task, Result]): Unit = {
    // send an empty progress report to clear out the previous report
    val event = ProgressEvent("Info", Vector(), Some(lastTaskCount.get), None, None)
    import sbt.internal.util.codec.JsonProtocol._
    log.logEvent(Level.Info, event)
  }
  private[this] val skipReportTasks =
    Set("run", "bgRun", "fgRun", "scala", "console", "consoleProject", "consoleQuick", "state")
  private[this] def maybeStartThread(): Unit = {
    currentProgressThread.get() match {
      case None =>
        currentProgressThread.synchronized {
          currentProgressThread.get() match {
            case None => currentProgressThread.set(Some(new ProgressThread))
            case _    =>
          }
        }
      case _ =>
    }
  }
  private[this] def report(): Unit = {
    val currentTasks = activeTasks.toVector.filterNot(Def.isDummy)
    val ltc = lastTaskCount.get
    val currentTasksCount = currentTasks.size
    def report0(tasks: Vector[Task[_]]): Unit = {
      if (tasks.nonEmpty) maybeStartThread()
      val event = ProgressEvent(
        "Info",
        tasks
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
    if (containsSkipTasks(currentTasks)) {
      if (ltc > 0) {
        lastTaskCount.set(0)
        report0(Vector.empty)
      }
    } else {
      lastTaskCount.set(currentTasksCount)
      report0(currentTasks)
    }
  }

  private[this] def containsSkipTasks(tasks: Vector[Task[_]]): Boolean =
    tasks
      .map(t => taskName(t))
      .exists(n => skipReportTasks.exists(m => m == n || n.endsWith("/ " + m)))
}
