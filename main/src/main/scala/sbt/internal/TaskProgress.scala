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
import java.util.concurrent.{
  CancellationException,
  ExecutionException,
  Future,
  RejectedExecutionException,
  TimeUnit
}

import sbt.internal.util._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import java.util.concurrent.{
  ConcurrentHashMap,
  LinkedBlockingQueue,
  ScheduledThreadPoolExecutor,
  ThreadPoolExecutor,
  TimeoutException
}
import sbt.util.Logger

/**
 * implements task progress display on the shell.
 */
private[sbt] class TaskProgress(
    sleepDuration: FiniteDuration,
    threshold: FiniteDuration,
    logger: Logger
) extends AbstractTaskExecuteProgress
    with ExecuteProgress[Task]
    with AutoCloseable {
  private[this] val lastTaskCount = new AtomicInteger(0)
  private[this] val reportLoop = new AtomicReference[AutoCloseable]
  private[this] val active = new ConcurrentHashMap[Task[_], AutoCloseable]
  private[this] val nextReport = new AtomicReference(Deadline.now)
  private[this] val reportPending = new AtomicBoolean(false)
  private[this] val scheduler = {
    val service = new ScheduledThreadPoolExecutor(
      1,
      r => new Thread(r, "sbt-progress-report-scheduler")
    )
    service.setRemoveOnCancelPolicy(true)
    service
  }
  private[this] val closed = new AtomicBoolean(false)
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
        () => {
          cancelled.set(true)
          Util.ignoreResult(future.cancel(true))
        }
      } catch {
        case e: RejectedExecutionException =>
          logger.trace(e)
          () => ()
      }
    } else {
      logger.debug("tried to call schedule on closed TaskProgress")
      () => ()
    }
  private[this] val executor = new ThreadPoolExecutor(
    1,
    1,
    0L,
    TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue[Runnable],
    r => new Thread(r, "sbt-task-progress-report-thread")
  )
  override def close(): Unit = if (closed.compareAndSet(false, true)) {
    Option(reportLoop.getAndSet(null)).foreach(_.close())
    active.values().forEach(_.close())
    scheduler.shutdownNow()
    executor.shutdownNow().asScala.foreach {
      case f: Future[_] => Util.ignoreResult(f.cancel(false))
      case _            =>
    }
    if (!executor.awaitTermination(30, TimeUnit.SECONDS) ||
        !scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
      scala.Console.err.println("timed out closing the executor of supershell")
      throw new TimeoutException
    }
    active.clear()
    clearTasks()
  }

  override protected def clearTimings: Boolean = true
  protected def completionTimeout: FiniteDuration = 10.seconds
  override def initial(): Unit = ()

  override private[sbt] def retainedTaskMetadata: Map[String, Set[Task[_]]] =
    super.retainedTaskMetadata.updated("active", active.keySet.asScala.toSet)
  private[sbt] def awaitIdle(): Unit =
    Util.ignoreResult(executor.submit((() => ()): Runnable).get(10, TimeUnit.SECONDS))
  private[sbt] def queuedCallbacks: Int = executor.getQueue.size
  private[sbt] def scheduledReports: Int = scheduler.getQueue.size
  private[sbt] def scheduledReportRuns: Long = scheduler.getCompletedTaskCount

  private[this] def doReport(): Unit =
    if (!closed.get && nextReport.get.isOverdue && reportPending.compareAndSet(false, true)) {
      try {
        executor.execute(() => {
          try {
            if (!closed.get && nextReport.get.isOverdue) report()
          } finally reportPending.set(false)
        })
      } catch { case _: RejectedExecutionException => reportPending.set(false) }
    }
  override def beforeWork(task: Task[_]): Unit =
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

  override def afterReady(task: Task[_]): Unit =
    if (!closed.get) {
      try {
        executor.execute(() => {
          if (skipReportTasks.contains(getShortName(task))) {
            lastTaskCount.set(-1) // force a report for remote clients
            report()
          } else
            Util.ignoreResult(active.put(task, schedule(threshold, recurring = false)(doReport())))
        })
      } catch { case _: RejectedExecutionException => }
    } else {
      logger.debug(s"called afterReady for ${taskName(task)} after task progress was closed")
    }
  override def stop(): Unit = {}

  override def afterCompleted[A](task: Task[A], result: Result[A]): Unit =
    if (!closed.get) {
      try {
        executor.execute(() => {
          active.remove(task) match {
            case null =>
            case a =>
              a.close()
              if (exceededThreshold(task, threshold)) report()
          }
          clearTask(task)
        })
      } catch { case _: RejectedExecutionException => }
    }

  override def afterAllCompleted(results: RMap[Task, Result]): Unit = {
    closeReportLoop()
    awaitCleanup(results.keys.foreach(clearTask))
  }

  override private[sbt] def afterAborted(tasks: Iterable[Task[_]]): Unit = {
    closeReportLoop()
    tasks.foreach(clearTimer)
    awaitCleanup {
      tasks.foreach { task =>
        active.remove(task) match {
          case null =>
          case a    => a.close()
        }
        clearTask(task)
      }
      clearReferencesTo(tasks.toSet)
    }
  }

  private[this] def closeReportLoop(): Unit =
    reportLoop.getAndSet(null) match {
      case null =>
      case l    => l.close()
    }

  private[this] def awaitCleanup(cleanup: => Unit): Unit = {
    // send an empty progress report to clear out the previous report
    if (!closed.get) {
      try {
        val runnable: Runnable = () => {
          cleanup
          appendProgress(ProgressEvent("Info", Vector(), Some(lastTaskCount.get), None, None))
        }
        Util.ignoreResult(
          executor.submit(runnable).get(completionTimeout.toMillis, TimeUnit.MILLISECONDS)
        )
      } catch {
        case _: InterruptedException => Thread.currentThread().interrupt()
        case _: TimeoutException =>
          logger.debug(s"task progress cleanup did not finish within $completionTimeout")
        case _: CancellationException      =>
        case e: ExecutionException         => throw e.getCause
        case _: RejectedExecutionException =>
      }
    }
  }
  private[this] val skipReportTasks =
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
  private[this] val hiddenTasks = Set(
    "compileEarly",
    "pickleProducts",
  )
  private[this] def appendProgress(event: ProgressEvent): Unit =
    StandardMain.exchange.updateProgress(event)
  private[this] def report(): Unit = {
    val (currentTasks, skip) = filter(timings(active.keySet, threshold.toMicros))
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
          Some(skip)
        )
      }
      lastTaskCount.set(currentTasksCount)
      appendProgress(event(currentTasks))
    }
  }

  private[this] def getShortName(task: Task[_]): String = {
    val name = taskName(task)
    name.lastIndexOf('/') match {
      case -1 => name
      case i =>
        var j = i + 1
        while (name(j) == ' ') j += 1
        name.substring(j)
    }

  }
  private[this] def filter(
      tasks: Vector[(Task[_], Long)]
  ): (Vector[(Task[_], Long)], Boolean) = {
    tasks.foldLeft((Vector.empty[(Task[_], Long)], false)) {
      case ((tasks, skip), pair @ (t, _)) =>
        val shortName = getShortName(t)
        val newSkip = skip || skipReportTasks.contains(shortName)
        if (hiddenTasks.contains(shortName)) (tasks, newSkip) else (tasks :+ pair, newSkip)
    }
  }
}
