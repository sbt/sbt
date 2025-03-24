/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
import sbt.internal.util.RMap

/**
 * Tracks command execution progress. In addition to ExecuteProgress, this interface
 * adds command start and end events, and gives access to the sbt.State at the beginning
 * and end of each command.
 *
 * Command progress callbacks are wrapping task progress callbacks. That is, the `beforeCommand`
 * callback will be called before the `initial` callback from ExecuteProgress, and the
 * `afterCommand` callback will be called after the `stop` callback from ExecuteProgress.
 */
trait ExecuteProgress2 extends ExecuteProgress {

  /**
   * Called before a command starts processing. The command has not yet been parsed.
   *
   * @param cmd The command string
   * @param state The sbt.State before the command starts executing.
   */
  def beforeCommand(cmd: String, state: State): Unit

  /**
   * Called after a command finished execution.
   *
   * @param cmd    The command string.
   * @param result Left in case of an error. If the command cannot be parsed, it will be
   *               signalled as a ParseException with a detailed message. If the command
   *               was cancelled by the user, as sbt.Cancelled. If the command succeeded,
   *               Right with the new state after command execution.
   */
  def afterCommand(cmd: String, result: Either[Throwable, State]): Unit
}

class ExecuteProgressAdapter(ep: ExecuteProgress) extends ExecuteProgress2 {
  override def beforeCommand(cmd: String, state: State): Unit = {}
  override def afterCommand(cmd: String, result: Either[Throwable, State]): Unit = {}
  override def initial(): Unit = ep.initial()
  override def afterRegistered(
      task: TaskId[?],
      allDeps: Iterable[TaskId[?]],
      pendingDeps: Iterable[TaskId[?]]
  ): Unit = ep.afterRegistered(task, allDeps, pendingDeps)
  override def afterReady(task: TaskId[?]): Unit = ep.afterReady(task)
  override def beforeWork(task: TaskId[?]): Unit = ep.beforeWork(task)
  override def afterWork[A](task: TaskId[A], result: Either[TaskId[A], Result[A]]): Unit =
    ep.afterWork(task, result)
  override def afterCompleted[A](task: TaskId[A], result: Result[A]): Unit =
    ep.afterCompleted(task, result)
  override def afterAllCompleted(results: RMap[TaskId, Result]): Unit =
    ep.afterAllCompleted(results)
  override def stop(): Unit = ep.stop()
}

object ExecuteProgress2 {
  def aggregate(xs: Seq[ExecuteProgress2]): ExecuteProgress2 = new ExecuteProgress2 {
    override def beforeCommand(cmd: String, state: State): Unit =
      xs.foreach(_.beforeCommand(cmd, state))
    override def afterCommand(cmd: String, result: Either[Throwable, State]): Unit =
      xs.foreach(_.afterCommand(cmd, result))
    override def initial(): Unit = xs.foreach(_.initial())
    override def afterRegistered(
        task: TaskId[?],
        allDeps: Iterable[TaskId[?]],
        pendingDeps: Iterable[TaskId[?]]
    ): Unit = xs.foreach(_.afterRegistered(task, allDeps, pendingDeps))
    override def afterReady(task: TaskId[?]): Unit = xs.foreach(_.afterReady(task))
    override def beforeWork(task: TaskId[?]): Unit = xs.foreach(_.beforeWork(task))
    override def afterWork[A](task: TaskId[A], result: Either[TaskId[A], Result[A]]): Unit =
      xs.foreach(_.afterWork(task, result))
    override def afterCompleted[A](task: TaskId[A], result: Result[A]): Unit =
      xs.foreach(_.afterCompleted(task, result))
    override def afterAllCompleted(results: RMap[TaskId, Result]): Unit =
      xs.foreach(_.afterAllCompleted(results))
    override def stop(): Unit = xs.foreach(_.stop())
  }
}
