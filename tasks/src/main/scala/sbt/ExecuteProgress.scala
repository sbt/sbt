/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.RMap

/**
 * Processes progress events during task execution.
 * All methods are called from the same thread except `started` and `finished`,
 * which is called from the executing task's thread.
 * All methods should return quickly to avoid task execution overhead.
 *
 * This class is experimental and subject to binary and source incompatible changes at any time.
 */
private[sbt] trait ExecuteProgress[F[_]] {
  type S
  def initial: S

  /**
   * Notifies that a `task` has been registered in the system for execution.
   * The dependencies of `task` are `allDeps` and the subset of those dependencies that
   * have not completed are `pendingDeps`.
   */
  def registered(state: S, task: F[_], allDeps: Iterable[F[_]], pendingDeps: Iterable[F[_]]): S

  /**
   * Notifies that all of the dependencies of `task` have completed and `task` is therefore
   * ready to run.  The task has not been scheduled on a thread yet.
   */
  def ready(state: S, task: F[_]): S

  /**
   * Notifies that the work for `task` is starting after this call returns.
   * This is called from the thread the task executes on, unlike most other methods in this callback.
   * It is called immediately before the task's work starts with minimal intervening executor overhead.
   */
  def workStarting(task: F[_]): Unit

  /**
   * Notifies that the work for `task` work has finished.  The task may have computed the next task to
   * run, in which case `result` contains that next task wrapped in Left.  If the task produced a value
   * or terminated abnormally, `result` provides that outcome wrapped in Right.  The ultimate result of
   * a task is provided to the `completed` method.
   * This is called from the thread the task executes on, unlike most other methods in this callback.
   * It is immediately called after the task's work is complete with minimal intervening executor overhead.
   */
  def workFinished[A](task: F[A], result: Either[F[A], Result[A]]): Unit

  /**
   * Notifies that `task` has completed.
   * The task's work is done with a final `result`.
   * Any tasks called by `task` have completed.
   */
  def completed[A](state: S, task: F[A], result: Result[A]): S

  /** All tasks have completed with the final `results` provided. */
  def allCompleted(state: S, results: RMap[F, Result]): S

  /** Notifies that either all tasks have finished or cancelled. */
  def stop(): Unit
}

/** This module is experimental and subject to binary and source incompatible changes at any time. */
private[sbt] object ExecuteProgress {
  def empty[F[_]]: ExecuteProgress[F] = new ExecuteProgress[F] {
    type S = Unit
    def initial = ()
    def registered(state: Unit, task: F[_], allDeps: Iterable[F[_]], pendingDeps: Iterable[F[_]]) =
      ()
    def ready(state: Unit, task: F[_]) = ()
    def workStarting(task: F[_]) = ()
    def workFinished[A](task: F[A], result: Either[F[A], Result[A]]) = ()
    def completed[A](state: Unit, task: F[A], result: Result[A]) = ()
    def allCompleted(state: Unit, results: RMap[F, Result]) = ()
    def stop(): Unit = ()
  }
}
