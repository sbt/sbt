package xsbt.test

trait StatementHandler
{
	type State
	def initialState: State
	def apply(command: String, arguments: List[String], state: State): State
}

trait BasicStatementHandler extends StatementHandler
{
	final type State = Unit
	final def initialState = ()
	final def apply(command: String, arguments: List[String], state: Unit): Unit= apply(command, arguments)
	def apply(command: String, arguments: List[String]): Unit
}