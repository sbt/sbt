/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
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
private[sbt] trait ExecuteProgress[A[_]] {
  type S
  def initial: S

  /**
   * Notifies that a `task` has been registered in the system for execution.
   * The dependencies of `task` are `allDeps` and the subset of those dependencies that
   * have not completed are `pendingDeps`.
   */
  def registered(state: S, task: A[_], allDeps: Iterable[A[_]], pendingDeps: Iterable[A[_]]): S

  /**
   * Notifies that all of the dependencies of `task` have completed and `task` is therefore
   * ready to run.  The task has not been scheduled on a thread yet.
   */
  def ready(state: S, task: A[_]): S

  /**
   * Notifies that the work for `task` is starting after this call returns.
   * This is called from the thread the task executes on, unlike most other methods in this callback.
   * It is called immediately before the task's work starts with minimal intervening executor overhead.
   */
  def workStarting(task: A[_]): Unit

  /**
   * Notifies that the work for `task` work has finished.  The task may have computed the next task to
   * run, in which case `result` contains that next task wrapped in Left.  If the task produced a value
   * or terminated abnormally, `result` provides that outcome wrapped in Right.  The ultimate result of
   * a task is provided to the `completed` method.
   * This is called from the thread the task executes on, unlike most other methods in this callback.
   * It is immediately called after the task's work is complete with minimal intervening executor overhead.
   */
  def workFinished[T](task: A[T], result: Either[A[T], Result[T]]): Unit

  /**
   * Notifies that `task` has completed.
   * The task's work is done with a final `result`.
   * Any tasks called by `task` have completed.
   */
  def completed[T](state: S, task: A[T], result: Result[T]): S

  /** All tasks have completed with the final `results` provided. */
  def allCompleted(state: S, results: RMap[A, Result]): S
}

/** This module is experimental and subject to binary and source incompatible changes at any time. */
private[sbt] object ExecuteProgress {
  def empty[A[_]]: ExecuteProgress[A] = new ExecuteProgress[A] {
    type S = Unit
    def initial = ()
    def registered(state: Unit, task: A[_], allDeps: Iterable[A[_]], pendingDeps: Iterable[A[_]]) =
      ()
    def ready(state: Unit, task: A[_]) = ()
    def workStarting(task: A[_]) = ()
    def workFinished[T](task: A[T], result: Either[A[T], Result[T]]) = ()
    def completed[T](state: Unit, task: A[T], result: Result[T]) = ()
    def allCompleted(state: Unit, results: RMap[A, Result]) = ()
  }
}
