/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

	import java.io.File
	import java.util.concurrent.Callable
	import CommandSupport.{FailureWall, logger}

/**
Data structure representing all command execution information.

@param `configuration` provides access to the launcher environment, including the application configuration, Scala versions, jvm/filesystem wide locking, and the launcher itself
@param definedCommands the list of command definitions that evaluate command strings.  These may be modified to change the available commands.
@param onFailure the command to execute when another command fails.  `onFailure` is cleared before the failure handling command is executed.
@param remainingCommands the sequence of commands to execute.  This sequence may be modified to change the commands to be executed.  Typically, the `::` and `:::` methods are used to prepend new commands to run.
@param exitHooks code to run before sbt exits, usually to ensure resources are cleaned up.
@param history tracks the recently executed commands
@param attributes custom command state.  It is important to clean up attributes when no longer needed to avoid memory leaks and class loader leaks.
@param next the next action for the command processor to take.  This may be to continue with the next command, adjust global logging, or exit.
*/
final case class State(
	configuration: xsbti.AppConfiguration,
	definedCommands: Seq[Command],
	exitHooks: Set[ExitHook],
	onFailure: Option[String],
	remainingCommands: Seq[String],
	history: State.History,
	attributes: AttributeMap,
	next: State.Next
) extends Identity {
	lazy val combinedParser = Command.combine(definedCommands)(this)
}

trait Identity {
	override final def hashCode = super.hashCode
	override final def equals(a: Any) = super.equals(a)
	override final def toString = super.toString
}

/** Convenience methods for State transformations and operations. */
trait StateOps {
	def process(f: (String, State) => State): State

	/** Schedules `commands` to be run before any remaining commands.*/
	def ::: (commands: Seq[String]): State

	/** Schedules `command` to be run before any remaining commands.*/
	def :: (command: String): State

	/** Sets the next command processing action to be to continue processing the next command.*/
	def continue: State

	/** Reboots sbt.  A reboot restarts execution from the entry point of the launcher.
	* A reboot is designed to be as close as possible to actually restarting the JVM without actually doing so.
	* Because the JVM is not restarted, JVM exit hooks are not run.
	* State.exitHooks should be used instead and those will be run before rebooting.
	* If `full` is true, the boot directory is deleted before starting again.
	* This command is currently implemented to not return, but may be implemented in the future to only reboot at the next command processing step. */
	def reboot(full: Boolean): State

	/** Sets the next command processing action to do.*/
	def setNext(n: State.Next): State

	@deprecated("Use setNext", "0.11.0") def setResult(ro: Option[xsbti.MainResult]): State

	/** Restarts sbt without dropping loaded Scala classes.  It is a shallower restart than `reboot`.*/
	def reload: State

	/** Sets the next command processing action to be to rotate the global log and continue executing commands.*/
	def clearGlobalLog: State
	/** Sets the next command processing action to be to keep the previous log and continue executing commands.  */
	def keepLastLog: State

	/** Sets the next command processing action to be to exit with a zero exit code if `ok` is true and a nonzero exit code if `ok` if false.*/
	def exit(ok: Boolean): State
	/** Marks the currently executing command as failing.  This triggers failure handling by the command processor.  See also `State.onFailure`*/
	def fail: State

	/** Schedules `newCommands` to be run after any remaining commands. */
	def ++ (newCommands: Seq[Command]): State
	/** Schedules `newCommand` to be run after any remaining commands. */
	def + (newCommand: Command): State

	/** Gets the value associated with `key` from the custom attributes map.*/
	def get[T](key: AttributeKey[T]): Option[T]
	/** Sets the value associated with `key` in the custom attributes map.*/
	def put[T](key: AttributeKey[T], value: T): State
	/** Removes the `key` and any associated value from the custom attributes map.*/
	def remove(key: AttributeKey[_]): State
	/** Sets the value associated with `key` in the custom attributes map by transforming the current value.*/
	def update[T](key: AttributeKey[T])(f: Option[T] => T): State
	/** Returns true if `key` exists in the custom attributes map, false if it does not exist.*/
	def has(key: AttributeKey[_]): Boolean

	/** The application base directory, which is not necessarily the current working directory.*/
	def baseDir: File

	/** The Logger used for general command logging.*/
	def log: Logger

	/** Evaluates the provided expression with a JVM-wide and machine-wide lock on `file`.*/
	def locked[T](file: File)(t: => T): T

	/** Runs any defined exitHooks and then clears them.*/
	def runExitHooks(): State
	/** Registers a new exit hook, which will run when sbt exits or restarts.*/
	def addExitHook(f: => Unit): State
}

object State
{
	/** Represents the next action for the command processor.*/
	sealed trait Next
	/** Indicates that the command processor should process the next command.*/
	object Continue extends Next
	/** Indicates that the application should exit with the given result.*/
	final class Return(val result: xsbti.MainResult) extends Next
	/** Indicates that global logging should be rotated.*/
	final object ClearGlobalLog extends Next
	/** Indicates that the previous log file should be preserved instead of discarded.*/
	final object KeepLastLog extends Next

	/** Provides a list of recently executed commands.  The commands are stored as processed instead of as entered by the user.
	* @param executed the list of the most recently executed commands, with the most recent command first.
	* @maxSize the maximum number of commands to keep, or 0 to keep an unlimited number. */
	final class History private[State](val executed: Seq[String], val maxSize: Int)
	{
		/** Adds `command` as the most recently executed command.*/
		def :: (command: String): History =
		{
			val prependTo = if(maxSize > 0 && executed.size >= maxSize) executed.take(maxSize - 1) else executed
			new History(command +: prependTo, maxSize)
		}
		/** Changes the maximum number of commands kept, adjusting the current history if necessary.*/
		def setMaxSize(size: Int): History =
			new History(if(size <= 0) executed else executed.take(size), size)
		def current: String = executed.head
		def previous: Option[String] = executed.drop(1).headOption
	}
	/** Constructs an empty command History with a default, finite command limit.*/
	def newHistory = new History(Vector.empty, complete.HistoryCommands.MaxLines)

	def defaultReload(state: State): Reboot =
	{
		val app = state.configuration.provider
		new Reboot(app.scalaProvider.version, state.remainingCommands, app.id, state.configuration.baseDirectory)
	}

	/** Provides operations and transformations on State. */
	implicit def stateOps(s: State): StateOps = new StateOps {
		def process(f: (String, State) => State): State =
			s.remainingCommands match {
				case Seq(x, xs @ _*) => f(x, s.copy(remainingCommands = xs, history = x :: s.history))
				case Seq() => exit(true)
			}
			s.copy(remainingCommands = s.remainingCommands.drop(1))
		def ::: (newCommands: Seq[String]): State = s.copy(remainingCommands = newCommands ++ s.remainingCommands)
		def :: (command: String): State = (command :: Nil) ::: this
		def ++ (newCommands: Seq[Command]): State = s.copy(definedCommands = (s.definedCommands ++ newCommands).distinct)
		def + (newCommand: Command): State = this ++ (newCommand :: Nil)
		def baseDir: File = s.configuration.baseDirectory
		def setNext(n: Next) = s.copy(next = n)
		def setResult(ro: Option[xsbti.MainResult]) = ro match { case None => continue; case Some(r) => setNext(new Return(r)) }
		def continue = setNext(Continue)
		def reboot(full: Boolean) = throw new xsbti.FullReload(s.remainingCommands.toArray, full)
		def reload = setNext(new Return(defaultReload(s)))
		def clearGlobalLog = setNext(ClearGlobalLog)
		def keepLastLog = setNext(KeepLastLog)
		def exit(ok: Boolean) = setNext(new Return(Exit(if(ok) 0 else 1)))
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