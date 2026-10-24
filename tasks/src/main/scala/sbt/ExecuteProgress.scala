/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.function.BooleanSupplier
import sbt.internal.util.RMap

/**
 * Processes progress events during task execution. All methods are called from the same thread
 * except `started` and `finished`, which is called from the executing task's thread. All methods
 * should return quickly to avoid task execution overhead.
 */
trait ExecuteProgress:
  def initial(): Unit

  /**
   * Notifies that a `task` has been registered in the system for execution. The dependencies of
   * `task` are `allDeps` and the subset of those dependencies that have not completed are
   * `pendingDeps`.
   */
  def afterRegistered(
      task: TaskId[?],
      allDeps: Iterable[TaskId[?]],
      pendingDeps: Iterable[TaskId[?]]
  ): Unit

  /**
   * Notifies that all of the dependencies of `task` have completed and `task` is therefore ready to
   * run. The task has not been scheduled on a thread yet.
   */
  def afterReady(task: TaskId[?]): Unit

  /**
   * Notifies that the work for `task` is starting after this call returns. This is called from the
   * thread the task executes on, unlike most other methods in this callback. It is called
   * immediately before the task's work starts with minimal intervening executor overhead.
   */
  def beforeWork(task: TaskId[?]): Unit

  /**
   * Notifies that the work for `task` work has finished. The task may have computed the next task
   * to run, in which case `result` contains that next task wrapped in Left. If the task produced a
   * value or terminated abnormally, `result` provides that outcome wrapped in Right. The ultimate
   * result of a task is provided to the `completed` method. This is called from the thread the task
   * executes on, unlike most other methods in this callback. It is immediately called after the
   * task's work is complete with minimal intervening executor overhead.
   */
  def afterWork[A](task: TaskId[A], result: Either[TaskId[A], Result[A]]): Unit

  /**
   * Notifies that `task` has completed. The task's work is done with a final `result`. Any tasks
   * called by `task` have completed.
   */
  def afterCompleted[A](task: TaskId[A], result: Result[A]): Unit

  /** All tasks have completed with the final `results` provided. */
  def afterAllCompleted(results: RMap[TaskId, Result]): Unit

  /**
   * Notifies that the evaluation ended abnormally, before `afterAllCompleted`, for example because a
   * task was cancelled. `tasks` are all tasks the evaluation registered, including those that have
   * not completed. This is called instead of `afterAllCompleted`, after waiting up to one second
   * for `beforeWork` and `afterWork` calls that are still in progress. Apart from `stop`, no further
   * callbacks are made for this evaluation, also not from tasks that are still running. Only a
   * `beforeWork` or `afterWork` call that is still in progress after that wait can still finish
   * afterwards. `aggregate` stops passing such a call on to further reporters once it observes the
   * abort, but a reporter that already received it can still finish it after this notification.
   */
  def afterAborted(tasks: Iterable[TaskId[?]]): Unit = ()

  /** Notifies that either all tasks have finished or cancelled. */
  def stop(): Unit
end ExecuteProgress

/**
 * This module is experimental and subject to binary and source incompatible changes at any time.
 */
object ExecuteProgress:
  def empty: ExecuteProgress = new ExecuteProgress:
    override def initial(): Unit = ()
    override def afterRegistered(
        task: TaskId[?],
        allDeps: Iterable[TaskId[?]],
        pendingDeps: Iterable[TaskId[?]]
    ): Unit =
      ()
    override def afterReady(task: TaskId[?]): Unit = ()
    override def beforeWork(task: TaskId[?]): Unit = ()
    override def afterWork[A](task: TaskId[A], result: Either[TaskId[A], Result[A]]): Unit = ()
    override def afterCompleted[A](task: TaskId[A], result: Result[A]): Unit = ()
    override def afterAllCompleted(results: RMap[TaskId, Result]): Unit = ()
    override def stop(): Unit = ()

  def aggregate(reporters: Seq[ExecuteProgress]) = new ExecuteProgress:
    override def initial(): Unit =
      reporters foreach { _.initial() }
    override def afterRegistered(
        task: TaskId[?],
        allDeps: Iterable[TaskId[?]],
        pendingDeps: Iterable[TaskId[?]]
    ): Unit =
      reporters foreach { _.afterRegistered(task, allDeps, pendingDeps) }
    override def afterReady(task: TaskId[?]): Unit =
      reporters foreach { _.afterReady(task) }
    override def beforeWork(task: TaskId[?]): Unit =
      passWorkReport(reporters)(_.beforeWork(task))
    override def afterWork[A](task: TaskId[A], result: Either[TaskId[A], Result[A]]): Unit =
      passWorkReport(reporters)(_.afterWork(task, result))
    override def afterCompleted[A](task: TaskId[A], result: Result[A]): Unit =
      reporters foreach { _.afterCompleted(task, result) }
    override def afterAllCompleted(results: RMap[TaskId, Result]): Unit =
      reporters foreach { _.afterAllCompleted(results) }
    override def afterAborted(tasks: Iterable[TaskId[?]]): Unit =
      notifyEach(reporters)(_.afterAborted(tasks))
    override def stop(): Unit =
      reporters foreach { _.stop() }

  private val workReport = new ThreadLocal[BooleanSupplier]

  /**
   * Runs `report`, a `beforeWork` or `afterWork` call, so that the aggregates stop passing it on to
   * further reporters once `stopped` is true.
   */
  private[sbt] def reportingWork(stopped: BooleanSupplier)(report: => Unit): Unit =
    val previous = workReport.get
    workReport.set(stopped)
    try report
    finally workReport.set(previous)

  /**
   * Passes a `beforeWork` or `afterWork` call on to `xs`, and stops before the next reporter once
   * the call's evaluation was aborted. This is best effort: a reporter that already received the
   * call can still finish it after the abort was reported.
   */
  private[sbt] def passWorkReport[A](xs: Iterable[A])(call: A => Unit): Unit =
    xs.iterator.takeWhile(_ => !workReportStopped).foreach(call)

  private def workReportStopped: Boolean = workReport.get match
    case null    => false
    case stopped => stopped.getAsBoolean

  /**
   * Calls `call` for each of `xs`, also after one of them failed, and then rethrows the first
   * failure with the others added as suppressed.
   */
  private[sbt] def notifyEach[A](xs: Iterable[A])(call: A => Unit): Unit =
    val failure = xs.foldLeft(Option.empty[Throwable]): (failed, x) =>
      try
        call(x)
        failed
      catch
        case e: Throwable =>
          if e.isInstanceOf[InterruptedException] then Thread.currentThread.interrupt()
          failed match
            case Some(first) =>
              if first ne e then first.addSuppressed(e)
              failed
            case None => Some(e)
    failure.foreach(throw _)
end ExecuteProgress
