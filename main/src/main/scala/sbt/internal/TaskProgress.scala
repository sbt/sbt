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
import scala.concurrent.duration._

/**
 * implements task progress display on the shell.
 */
private[sbt] final class TaskProgress(log: ManagedLogger)
    extends AbstractTaskExecuteProgress
    with ExecuteProgress[Task] {
  private[this] val lastTaskCount = new AtomicInteger(0)
  private[this] val currentProgressThread = new AtomicReference[Option[ProgressThread]](None)
  private[this] val sleepDuration = SysProp.supershellSleep.millis
  private[this] val threshold = 10.millis
  private[this] final class ProgressThread
      extends Thread("task-progress-report-thread")
      with AutoCloseable {
    private[this] val isClosed = new AtomicBoolean(false)
    private[this] val firstTime = new AtomicBoolean(true)
    setDaemon(true)
    start()
    @tailrec override def run(): Unit = {
      if (!isClosed.get()) {
        try {
          report()
          val duration =
            if (firstTime.compareAndSet(true, activeExceedingThreshold.nonEmpty)) threshold
            else sleepDuration
          Thread.sleep(duration.toMillis)
        } catch { case _: InterruptedException => isClosed.set(true) }
        run()
      }
    }

    override def close(): Unit = {
      isClosed.set(true)
      interrupt()
    }
  }

  override def initial(): Unit = ()

  override def beforeWork(task: Task[_]): Unit = {
    super.beforeWork(task)
    if (containsSkipTasks(Vector(task)) || lastTaskCount.get == 0) report()
  }
  override def afterReady(task: Task[_]): Unit = ()

  override def afterCompleted[A](task: Task[A], result: Result[A]): Unit = ()

  override def stop(): Unit = currentProgressThread.synchronized {
    currentProgressThread.getAndSet(None).foreach(_.close())
  }

  override def afterAllCompleted(results: RMap[Task, Result]): Unit = {
    // send an empty progress report to clear out the previous report
    appendProgress(ProgressEvent("Info", Vector(), Some(lastTaskCount.get), None, None))
  }
  private[this] val skipReportTasks =
    Set(
      "run",
      "runMain",
      "bgRun",
      "fgRun",
      "scala",
      "console",
      "consoleProject",
      "consoleQuick",
      "state"
    )
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
  private[this] def appendProgress(event: ProgressEvent): Unit = {
    import sbt.internal.util.codec.JsonProtocol._
    log.logEvent(Level.Info, event)
  }
  private[this] def active: Vector[Task[_]] = activeTasks.toVector.filterNot(Def.isDummy)
  private[this] def activeExceedingThreshold: Vector[(Task[_], Long)] = active.flatMap { task =>
    val elapsed = timings.get(task).currentElapsedMicros
    if (elapsed.micros > threshold) Some[(Task[_], Long)](task -> elapsed) else None
  }
  private[this] def report(): Unit = {
    val currentTasks = activeExceedingThreshold
    val ltc = lastTaskCount.get
    val currentTasksCount = currentTasks.size
    def event(tasks: Vector[(Task[_], Long)]): ProgressEvent = ProgressEvent(
      "Info",
      tasks
        .map { case (task, elapsed) => ProgressItem(taskName(task), elapsed) }
        .sortBy(_.elapsedMicros),
      Some(ltc),
      None,
      None
    )
    if (active.nonEmpty) maybeStartThread()
    if (containsSkipTasks(active)) {
      if (ltc > 0) {
        lastTaskCount.set(0)
        appendProgress(event(Vector.empty))
      }
    } else {
      lastTaskCount.set(currentTasksCount)
      appendProgress(event(currentTasks))
    }
  }

  private[this] def containsSkipTasks(tasks: Vector[Task[_]]): Boolean = {
    tasks.map(taskName).exists { n =>
      val shortName = n.lastIndexOf('/') match {
        case -1 => n
        case i =>
          var j = i + 1
          while (n(j) == ' ') j += 1
          n.substring(j)
      }
      skipReportTasks.contains(shortName)
    }
  }
}
