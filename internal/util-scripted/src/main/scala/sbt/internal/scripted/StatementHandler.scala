/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package scripted

trait StatementHandler {
  type State
  def initialState: State
  def apply(command: String, arguments: List[String], state: State): State
  def finish(state: State): Unit
}

trait BasicStatementHandler extends StatementHandler {
  final type State = Unit
  final def initialState = ()

  final def apply(command: String, arguments: List[String], state: Unit): Unit =
    apply(command, arguments)

  def apply(command: String, arguments: List[String]): Unit
  def finish(state: Unit) = ()
}

/** Use when a stack trace is not useful */
final class TestFailed(msg: String) extends RuntimeException(msg) {
  override def fillInStackTrace = this
}
