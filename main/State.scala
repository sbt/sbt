/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

case class State(project: Any)(
	val configuration: xsbti.AppConfiguration,
	val processors: Seq[Command],
	val exitHooks: Set[ExitHook],
	val onFailure: Option[String],
	val commands: Seq[String],
	val next: Next.Value
)

trait StateOps {
	def process(f: (String, State) => State): State
	def ::: (commands: Seq[String]): State
	def :: (command: String): State
	def continue: State
	def reload: State
	def exit(ok: Boolean): State
	def fail: State
}
object State
{
	implicit def stateOps(s: State): StateOps = new StateOps {
		def process(f: (String, State) => State): State =
			s.commands match {
				case x :: xs => f(x, s.copy()(commands = xs))
				case Nil => exit(true)
			}
			s.copy()(commands = s.commands.drop(1))
		def ::: (newCommands: Seq[String]): State = s.copy()(commands = newCommands ++ s.commands)
		def :: (command: String): State = s.copy()(commands = command +: s.commands)
		def setNext(n: Next.Value) = s.copy()(next = n)
		def continue = setNext(Next.Continue)
		def reload = setNext(Next.Reload)
		def exit(ok: Boolean) = setNext(if(ok) Next.Fail else Next.Done)
		def fail =
			s.onFailure match
			{
				case Some(c) => s.copy()(commands = c :: Nil, onFailure = None)
				case None => exit(ok = false)
			}
	}
}