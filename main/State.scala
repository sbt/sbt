/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

	import java.io.File
	import java.util.concurrent.Callable
	import CommandSupport.{FailureWall, logger}

final case class State(
	configuration: xsbti.AppConfiguration,
	definedCommands: Seq[Command],
	exitHooks: Set[ExitHook],
	onFailure: Option[String],
	remainingCommands: Seq[String],
	attributes: AttributeMap,
	result: Option[xsbti.MainResult]
) extends Identity {
	lazy val combinedParser = Command.combine(definedCommands)(this)
}

trait Identity {
	override final def hashCode = super.hashCode
	override final def equals(a: Any) = super.equals(a)
	override final def toString = super.toString
}

trait StateOps {
	def process(f: (String, State) => State): State
	def ::: (commands: Seq[String]): State
	def :: (command: String): State
	def continue: State
	def reboot(full: Boolean): State
	def setResult(n: Option[xsbti.MainResult]): State
	def reload: State
	def exit(ok: Boolean): State
	def fail: State
	def ++ (newCommands: Seq[Command]): State
	def + (newCommand: Command): State
	def get[T](key: AttributeKey[T]): Option[T]
	def put[T](key: AttributeKey[T], value: T): State
	def remove(key: AttributeKey[_]): State
	def update[T](key: AttributeKey[T])(f: Option[T] => T): State
	def has(key: AttributeKey[_]): Boolean
	def baseDir: File
	def log: Logger
	def locked[T](file: File)(t: => T): T
	def runExitHooks(): State
	def addExitHook(f: => Unit): State
}
object State
{
	def defaultReload(state: State): Reboot =
	{
		val app = state.configuration.provider
		new Reboot(app.scalaProvider.version, state.remainingCommands, app.id, state.configuration.baseDirectory)
	}

	implicit def stateOps(s: State): StateOps = new StateOps {
		def process(f: (String, State) => State): State =
			s.remainingCommands match {
				case Seq(x, xs @ _*) => f(x, s.copy(remainingCommands = xs))
				case Seq() => exit(true)
			}
			s.copy(remainingCommands = s.remainingCommands.drop(1))
		def ::: (newCommands: Seq[String]): State = s.copy(remainingCommands = newCommands ++ s.remainingCommands)
		def :: (command: String): State = (command :: Nil) ::: this
		def ++ (newCommands: Seq[Command]): State = s.copy(definedCommands = (s.definedCommands ++ newCommands).distinct)
		def + (newCommand: Command): State = this ++ (newCommand :: Nil)
		def baseDir: File = s.configuration.baseDirectory
		def setResult(n: Option[xsbti.MainResult]) = s.copy(result = n)
		def continue = setResult(None)
		def reboot(full: Boolean) = throw new xsbti.FullReload(s.remainingCommands.toArray, full)
		def reload = setResult(Some(defaultReload(s)))
		def exit(ok: Boolean) = setResult(Some(Exit(if(ok) 0 else 1)))
		def get[T](key: AttributeKey[T]) = s.attributes get key
		def put[T](key: AttributeKey[T], value: T) = s.copy(attributes = s.attributes.put(key, value))
		def update[T](key: AttributeKey[T])(f: Option[T] => T): State = put(key, f(get(key)))
		def has(key: AttributeKey[_]) = s.attributes contains key
		def remove(key: AttributeKey[_]) = s.copy(attributes = s.attributes remove key)
		def log = CommandSupport.logger(s)
		def fail =
		{
			val remaining = s.remainingCommands.dropWhile(_ != FailureWall)
			if(remaining.isEmpty)
				applyOnFailure(s, Nil, exit(ok = false))
			else
				applyOnFailure(s, remaining, s.copy(remainingCommands = remaining))
		}
		private[this] def applyOnFailure(s: State, remaining: Seq[String], noHandler: => State): State =
			s.onFailure match
			{
				case Some(c) => s.copy(remainingCommands = c +: remaining, onFailure = None)
				case None => noHandler
			}

		def addExitHook(act: => Unit): State =
			s.copy(exitHooks = s.exitHooks + ExitHook(act))
		def runExitHooks(): State = {
			ExitHooks.runExitHooks(s.exitHooks.toSeq)
			s.copy(exitHooks = Set.empty)
		}
		def locked[T](file: File)(t: => T): T =
			s.configuration.provider.scalaProvider.launcher.globalLock.apply(file, new Callable[T] { def call = t })
	}
}