/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt.test


final class TestException(statement: Statement, msg: String, exception: Throwable)
	extends RuntimeException(statement.linePrefix + " " + msg, exception)

class ScriptRunner
{
	import scala.collection.mutable.HashMap
	def apply(statements: List[(StatementHandler, Statement)])
	{
		val states = new HashMap[StatementHandler, Any]
		 def processStatement(handler: StatementHandler, statement: Statement)
		 {
			val state = states.getOrElseUpdate(handler, handler.initialState).asInstanceOf[handler.State]
			val nextState =
				try { Right( handler(statement.command, statement.arguments, state) ) }
				catch { case e: Exception => Left(e) }
			nextState match
			{
				case Left(err) =>
					if(statement.successExpected)
						throw new TestException(statement, "Command failed", err)
					else
						()
				case Right(s) =>
					if(statement.successExpected)
						states(handler) = s
					else
						throw new TestException(statement, "Command succeeded but failure was expected", null)
			}
		}
		statements.foreach { case (handler, _) => states(handler) = handler.initialState }
		statements foreach( Function.tupled(processStatement) )
	}
}