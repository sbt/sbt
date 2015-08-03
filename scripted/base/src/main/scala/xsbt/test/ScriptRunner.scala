/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt.test

final class TestException(statement: Statement, msg: String, exception: Throwable)
  extends RuntimeException(statement.linePrefix + " " + msg, exception)

class ScriptRunner {
  import scala.collection.mutable.HashMap
  def apply(statements: List[(StatementHandler, Statement)]): Unit = {
    val states = new HashMap[StatementHandler, Any]
    def processStatement(handler: StatementHandler, statement: Statement): Unit = {
      val state = states(handler).asInstanceOf[handler.State]
      val nextState =
        try { Right(handler(statement.command, statement.arguments, state)) }
        catch { case e: Exception => Left(e) }
      nextState match {
        case Left(err) =>
          if (statement.successExpected) {
            err match {
              case t: TestFailed => throw new TestException(statement, "Command failed: " + t.getMessage, null)
              case _             => throw new TestException(statement, "Command failed", err)
            }
          } else
            ()
        case Right(s) =>
          if (statement.successExpected)
            states(handler) = s
          else
            throw new TestException(statement, "Command succeeded but failure was expected", null)
      }
    }
    val handlers = Set() ++ statements.map(_._1)

    try {
      handlers.foreach { handler => states(handler) = handler.initialState }
      statements foreach (Function.tupled(processStatement))
    } finally {
      for (handler <- handlers; state <- states.get(handler)) {
        try { handler.finish(state.asInstanceOf[handler.State]) }
        catch { case e: Exception => () }
      }
    }
  }
}
