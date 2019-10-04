/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package scriptedtest

import java.util.concurrent.{ Executors, TimeUnit, TimeoutException }

import scala.collection.mutable
import scala.concurrent.duration._
import sbt.internal.scripted._

private[sbt] object BatchScriptRunner {
  type States = mutable.HashMap[StatementHandler, StatementHandler#State]
}

/** Defines an alternative script runner that allows batch execution. */
private[sbt] class BatchScriptRunner extends ScriptRunner with AutoCloseable {
  import BatchScriptRunner.States
  private[this] val service = Executors.newCachedThreadPool()

  /** Defines a method to run batched execution.
   *
   * @param statements The list of handlers and statements.
   * @param states The states of the runner. In case it's empty, inherited apply is called.
   */
  def apply(statements: List[(StatementHandler, Statement)], states: States): Unit = {
    if (states.isEmpty) super.apply(statements)
    else statements.foreach(st => processStatement(st._1, st._2, states))
  }

  def initStates(states: States, handlers: Seq[StatementHandler]): Unit =
    handlers.foreach(handler => states(handler) = handler.initialState)

  def cleanUpHandlers(handlers: Seq[StatementHandler], states: States): Unit = {
    for (handler <- handlers; state <- states.get(handler)) {
      try handler.finish(state.asInstanceOf[handler.State])
      catch { case _: Exception => () }
    }
  }

  private val timeout = 5.minutes
  def processStatement(handler: StatementHandler, statement: Statement, states: States): Unit = {
    val state = states(handler).asInstanceOf[handler.State]
    val nextStateFuture = service.submit(
      () =>
        try Right(handler(statement.command, statement.arguments, state))
        catch { case e: Exception => Left(e) }
    )
    try {
      nextStateFuture.get(timeout.toMillis, TimeUnit.MILLISECONDS) match {
        case Left(err) =>
          if (statement.successExpected) {
            err match {
              case t: TestFailed =>
                throw new TestException(statement, "Command failed: " + t.getMessage, null)
              case _ => throw new TestException(statement, "Command failed", err)
            }
          } else
            ()
        case Right(s) =>
          if (statement.successExpected)
            states(handler) = s
          else
            throw new TestException(statement, "Command succeeded but failure was expected", null)
      }
    } catch {
      case e: TimeoutException => throw new TestException(statement, "Command timed out", e)
    }
  }

  override def close(): Unit = service.shutdown()
}
