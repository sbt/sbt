/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

	import java.io.File
	import CommandSupport.FailureWall

case class State(project: Any)(
	val configuration: xsbti.AppConfiguration,
	val processors: Seq[Command],
	val exitHooks: Set[ExitHook],
	val onFailure: Option[String],
	val commands: Seq[String],
	val attributes: AttributeMap,
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
	def ++ (newCommands: Seq[Command]): State
	def + (newCommand: Command): State
	def get[T](key: AttributeKey[T]): Option[T]
	def put[T](key: AttributeKey[T], value: T): State
	def baseDir: File
}
object State
{
	implicit def stateOps(s: State): StateOps = new StateOps {
		def process(f: (String, State) => State): State =
			s.commands match {
				case Seq(x, xs @ _*) => f(x, s.copy()(commands = xs))
				case Seq() => exit(true)
			}
			s.copy()(commands = s.commands.drop(1))
		def ::: (newCommands: Seq[String]): State = s.copy()(commands = newCommands ++ s.commands)
		def :: (command: String): State = (command :: Nil) ::: this
		def ++ (newCommands: Seq[Command]): State = s.copy()(processors = s.processors ++ newCommands)
		def + (newCommand: Command): State = this ++ (newCommand :: Nil)
		def baseDir: File = s.configuration.baseDirectory
		def setNext(n: Next.Value) = s.copy()(next = n)
		def continue = setNext(Next.Continue)
		def reload = setNext(Next.Reload)
		def exit(ok: Boolean) = setNext(if(ok) Next.Fail else Next.Done)
		def get[T](key: AttributeKey[T]) = s.attributes.get(key)
		def put[T](key: AttributeKey[T], value: T) = s.copy()(attributes = s.attributes.put(key, value))
		def fail =
		{
			val remaining = s.commands.dropWhile(_ != FailureWall)
			if(remaining.isEmpty)
			{
				s.onFailure match
				{
					case Some(c) => s.copy()(commands = c :: Nil, onFailure = None)
					case None => exit(ok = false)
				}
			}
			else
				s.copy()(commands = remaining)
		}
	}
}