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
 */
trait ExecuteProgress2 extends ExecuteProgress[Task] {

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
   *               was cancelled by the user, as sbt.Cancelled.
   */
  def afterCommand(cmd: String, result: Either[Throwable, State]): Unit
}

class ExecuteProgressAdapter(ep: ExecuteProgress[Task]) extends ExecuteProgress2 {
  override def beforeCommand(cmd: String, state: State): Unit = {}
  override def afterCommand(cmd: String, result: Either[Throwable, State]): Unit = {}
  override def initial(): Unit = ep.initial()
  override def afterRegistered(
      task: Task[_],
      allDeps: Iterable[Task[_]],
      pendingDeps: Iterable[Task[_]]
  ): Unit = ep.afterRegistered(task, allDeps, pendingDeps)
  override def afterReady(task: Task[_]): Unit = ep.afterReady(task)
  override def beforeWork(task: Task[_]): Unit = ep.beforeWork(task)
  override def afterWork[A](task: Task[A], result: Either[Task[A], Result[A]]): Unit =
    ep.afterWork(task, result)
  override def afterCompleted[A](task: Task[A], result: Result[A]): Unit =
    ep.afterCompleted(task, result)
  override def afterAllCompleted(results: RMap[Task, Result]): Unit = ep.afterAllCompleted(results)
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
        task: Task[_],
        allDeps: Iterable[Task[_]],
        pendingDeps: Iterable[Task[_]]
    ): Unit = xs.foreach(_.afterRegistered(task, allDeps, pendingDeps))
    override def afterReady(task: Task[_]): Unit = xs.foreach(_.afterReady(task))
    override def beforeWork(task: Task[_]): Unit = xs.foreach(_.beforeWork(task))
    override def afterWork[A](task: Task[A], result: Either[Task[A], Result[A]]): Unit =
      xs.foreach(_.afterWork(task, result))
    override def afterCompleted[A](task: Task[A], result: Result[A]): Unit =
      xs.foreach(_.afterCompleted(task, result))
    override def afterAllCompleted(results: RMap[Task, Result]): Unit =
      xs.foreach(_.afterAllCompleted(results))
    override def stop(): Unit = xs.foreach(_.stop())
  }
}
