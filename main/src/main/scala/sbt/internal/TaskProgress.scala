/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import java.util.concurrent.{ RejectedExecutionException, TimeUnit }

import sbt.internal.util._

import scala.jdk.CollectionConverters.*
import scala.concurrent.duration._
import java.util.concurrent.{ ConcurrentHashMap, Executors, TimeoutException }
import sbt.util.Logger

/**
 * implements task progress display on the shell.
 */
private[sbt] class TaskProgress(
    sleepDuration: FiniteDuration,
    threshold: FiniteDuration,
    logger: Logger
) extends AbstractTaskExecuteProgress
    with ExecuteProgress
    with AutoCloseable {
  private val lastTaskCount = new AtomicInteger(0)
  private val reportLoop = new AtomicReference[AutoCloseable]
  private val active = new ConcurrentHashMap[TaskId[_], AutoCloseable]
  private val nextReport = new AtomicReference(Deadline.now)
  private val scheduler =
    Executors.newSingleThreadScheduledExecutor(r => new Thread(r, "sbt-progress-report-scheduler"))
  private val pending = new java.util.Vector[java.util.concurrent.Future[_]]
  private val closed = new AtomicBoolean(false)
  private def schedule[R](duration: FiniteDuration, recurring: Boolean)(f: => R): AutoCloseable =
    if (!closed.get) {
      val cancelled = new AtomicBoolean(false)
      val runnable: Runnable = () => {
        if (!cancelled.get) {
          try Util.ignoreResult(f)
          catch { case _: InterruptedException => }
        }
      }
      val delay = duration.toMillis
      try {
        val future =
          if (recurring)
            scheduler.scheduleAtFixedRate(runnable, delay, delay, TimeUnit.MILLISECONDS)
          else scheduler.schedule(runnable, delay, TimeUnit.MILLISECONDS)
        pending.add(future)
        () => Util.ignoreResult(future.cancel(true))
      } catch {
        case e: RejectedExecutionException =>
          logger.trace(e)
          () => ()
      }
    } else {
      logger.debug("tried to call schedule on closed TaskProgress")
      () => ()
    }
  private val executor =
    Executors.newSingleThreadExecutor(r => new Thread(r, "sbt-task-progress-report-thread"))
  override def close(): Unit = if (closed.compareAndSet(false, true)) {
    Option(reportLoop.getAndSet(null)).foreach(_.close())
    pending.forEach(f => Util.ignoreResult(f.cancel(true)))
    pending.clear()
    scheduler.shutdownNow()
    executor.shutdownNow()
    if (
      !executor.awaitTermination(30, TimeUnit.SECONDS) ||
      !scheduler.awaitTermination(30, TimeUnit.SECONDS)
    ) {
      scala.Console.err.println("timed out closing the executor of supershell")
      throw new TimeoutException
    }
  }

  override protected def clearTimings: Boolean = true
  override def initial(): Unit = ()

  private def doReport(): Unit = {
    val runnable: Runnable = () => {
      if (nextReport.get.isOverdue) {
        report()
      }
    }
    Util.ignoreResult(pending.add(executor.submit(runnable)))
  }
  override def beforeWork(task: TaskId[?]): Unit =
    if (!closed.get) {
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
    } else {
      logger.debug(s"called beforeWork for ${taskName(task)} after task progress was closed")
    }

  override def afterReady(task: TaskId[?]): Unit =
    if (!closed.get) {
      try {
        Util.ignoreResult(executor.submit((() => {
          if (skipReportTasks.contains(getShortName(task))) {
            lastTaskCount.set(-1) // force a report for remote clients
            report()
          } else
            Util.ignoreResult(active.put(task, schedule(threshold, recurring = false)(doReport())))
        }): Runnable))
      } catch { case _: RejectedExecutionException => }
    } else {
      logger.debug(s"called afterReady for ${taskName(task)} after task progress was closed")
    }
  override def stop(): Unit = {}

  override def afterCompleted[A](task: TaskId[A], result: Result[A]): Unit =
    active.remove(task) match {
      case null =>
      case a =>
        a.close()
        if (exceededThreshold(task, threshold)) report()
    }

  override def afterAllCompleted(results: RMap[TaskId, Result]): Unit = {
    reportLoop.getAndSet(null) match {
      case null =>
      case l    => l.close()
    }
    // send an empty progress report to clear out the previous report
    appendProgress(ProgressEvent("Info", Vector(), Some(lastTaskCount.get), None, None))
  }
  private val skipReportTasks =
    Set(
      "installSbtn",
      "run",
      "runMain",
      "bgRun",
      "fgRun",
      "scala",
      "console",
      "consoleProject",
      "consoleQuick",
      "state",
      "streams",
      "streams-manager",
    )
  private val hiddenTasks = Set(
    "compileEarly",
    "pickleProducts",
  )
  private def appendProgress(event: ProgressEvent): Unit =
    StandardMain.exchange.updateProgress(event)
  private def report(): Unit = {
    val (currentTasks, skip) = filter(timings(active.keySet, threshold.toMicros))
    val ltc = lastTaskCount.get
    if (currentTasks.nonEmpty || ltc != 0) {
      val currentTasksCount = currentTasks.size
      def event(tasks: Vector[(TaskId[_], Long)]): ProgressEvent = {
        if (tasks.nonEmpty) nextReport.set(Deadline.now + sleepDuration)
        val toWrite = tasks.sortBy(_._2)
        val distinct = new java.util.LinkedHashMap[String, ProgressItem]
        toWrite.foreach { case (task, elapsed) =>
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
          Some(skip)
        )
      }
      lastTaskCount.set(currentTasksCount)
      appendProgress(event(currentTasks))
    }
  }

  private def getShortName(task: TaskId[_]): String = {
    val name = taskName(task)
    name.lastIndexOf('/') match {
      case -1 => name
      case i =>
        var j = i + 1
        while (name(j) == ' ') j += 1
        name.substring(j)
    }

  }
  private def filter(
      tasks: Vector[(TaskId[_], Long)]
  ): (Vector[(TaskId[_], Long)], Boolean) = {
    tasks.foldLeft((Vector.empty[(TaskId[_], Long)], false)) {
      case ((tasks, skip), pair @ (t, _)) =>
        val shortName = getShortName(t)
        val newSkip = skip || skipReportTasks.contains(shortName)
        if (hiddenTasks.contains(shortName)) (tasks, newSkip) else (tasks :+ pair, newSkip)
    }
  }
}
