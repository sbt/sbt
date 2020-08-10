/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import java.util.concurrent.TimeUnit

import sbt.internal.util._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import java.util.concurrent.{ ConcurrentHashMap, Executors, TimeoutException }

/**
 * implements task progress display on the shell.
 */
private[sbt] class TaskProgress(sleepDuration: FiniteDuration, threshold: FiniteDuration)
    extends AbstractTaskExecuteProgress
    with ExecuteProgress[Task]
    with AutoCloseable {
  private[this] val lastTaskCount = new AtomicInteger(0)
  private[this] val reportLoop = new AtomicReference[AutoCloseable]
  private[this] val active = new ConcurrentHashMap[Task[_], AutoCloseable]
  private[this] val nextReport = new AtomicReference(Deadline.now)
  private[this] val scheduler =
    Executors.newSingleThreadScheduledExecutor(r => new Thread(r, "sbt-progress-report-scheduler"))
  private[this] val pending = new java.util.Vector[java.util.concurrent.Future[_]]
  private def schedule[R](duration: FiniteDuration, recurring: Boolean)(f: => R): AutoCloseable = {
    val cancelled = new AtomicBoolean(false)
    val runnable: Runnable = () => {
      if (!cancelled.get) {
        try Util.ignoreResult(f)
        catch { case _: InterruptedException => }
      }
    }
    val delay = duration.toMillis
    val future =
      if (recurring) scheduler.schedule(runnable, delay, TimeUnit.MILLISECONDS)
      else scheduler.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
    pending.add(future)
    () => Util.ignoreResult(future.cancel(true))
  }
  private[this] val executor =
    Executors.newSingleThreadExecutor(r => new Thread(r, "sbt-task-progress-report-thread"))
  override def close(): Unit = {
    Option(reportLoop.get).foreach(_.close())
    pending.forEach(f => Util.ignoreResult(f.cancel(true)))
    pending.clear()
    scheduler.shutdownNow()
    executor.shutdownNow()
    if (!executor.awaitTermination(1, TimeUnit.SECONDS) ||
        !scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
      throw new TimeoutException
    }
  }

  override protected def clearTimings: Boolean = true
  override def initial(): Unit = ()

  private[this] def doReport(): Unit = {
    val runnable: Runnable = () => {
      if (nextReport.get.isOverdue) {
        report()
      }
    }
    Util.ignoreResult(pending.add(executor.submit(runnable)))
  }
  override def beforeWork(task: Task[_]): Unit = {
    super.beforeWork(task)
    reportLoop.get match {
      case null =>
        val loop = schedule(sleepDuration, recurring = true)(doReport())
        reportLoop.getAndSet(loop) match {
          case null =>
          case l =>
            reportLoop.set(l)
            loop.close()
        }
      case s =>
    }
  }

  override def afterReady(task: Task[_]): Unit =
    Util.ignoreResult(active.put(task, schedule(threshold, recurring = false)(doReport())))
  override def stop(): Unit = {}

  override def afterCompleted[A](task: Task[A], result: Result[A]): Unit =
    active.remove(task) match {
      case null =>
      case a =>
        a.close()
        if (exceededThreshold(task, threshold)) report()
    }

  override def afterAllCompleted(results: RMap[Task, Result]): Unit = {
    reportLoop.getAndSet(null) match {
      case null =>
      case l    => l.close()
    }
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
  private[this] def appendProgress(event: ProgressEvent): Unit =
    StandardMain.exchange.updateProgress(event)
  private[this] def report(): Unit = {
    val currentTasks = timings(active.keySet, threshold.toMicros)
    val ltc = lastTaskCount.get
    if (currentTasks.nonEmpty || ltc != 0) {
      val currentTasksCount = currentTasks.size
      def event(tasks: Vector[(Task[_], Long)]): ProgressEvent = {
        if (tasks.nonEmpty) nextReport.set(Deadline.now + sleepDuration)
        val toWrite = tasks.sortBy(_._2)
        val distinct = new java.util.LinkedHashMap[String, ProgressItem]
        toWrite.foreach {
          case (task, elapsed) =>
            val name = taskName(task)
            distinct.put(name, ProgressItem(name, elapsed))
        }
        ProgressEvent(
          "Info",
          distinct.values.asScala.toVector,
          Some(ltc),
          None,
          None,
          None,
          Some(containsSkipTasks(active.keySet))
        )
      }
      lastTaskCount.set(currentTasksCount)
      appendProgress(event(currentTasks))
    }
  }

  private[this] def containsSkipTasks(tasks: java.util.Set[Task[_]]): Boolean = {
    tasks.iterator.asScala.map(taskName).exists { n =>
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
