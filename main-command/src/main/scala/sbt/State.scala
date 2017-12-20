/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import java.util.concurrent.Callable
import sbt.util.Logger
import sbt.internal.util.{
  AttributeKey,
  AttributeMap,
  ErrorHandling,
  ExitHook,
  ExitHooks,
  GlobalLogging
}
import sbt.internal.util.complete.HistoryCommands
import sbt.internal.inc.classpath.ClassLoaderCache

/**
 * Data structure representing all command execution information.
 *
 * @param configuration provides access to the launcher environment, including the application configuration, Scala versions, jvm/filesystem wide locking, and the launcher itself
 * @param definedCommands the list of command definitions that evaluate command strings.  These may be modified to change the available commands.
 * @param onFailure the command to execute when another command fails.  `onFailure` is cleared before the failure handling command is executed.
 * @param remainingCommands the sequence of commands to execute.  This sequence may be modified to change the commands to be executed.  Typically, the `::` and `:::` methods are used to prepend new commands to run.
 * @param exitHooks code to run before sbt exits, usually to ensure resources are cleaned up.
 * @param history tracks the recently executed commands
 * @param attributes custom command state.  It is important to clean up attributes when no longer needed to avoid memory leaks and class loader leaks.
 * @param next the next action for the command processor to take.  This may be to continue with the next command, adjust global logging, or exit.
 */
final case class State(
    configuration: xsbti.AppConfiguration,
    definedCommands: Seq[Command],
    exitHooks: Set[ExitHook],
    onFailure: Option[Exec],
    remainingCommands: List[Exec],
    history: State.History,
    attributes: AttributeMap,
    globalLogging: GlobalLogging,
    currentCommand: Option[Exec],
    next: State.Next
) extends Identity {
  lazy val combinedParser = Command.combine(definedCommands)(this)

  def source: Option[CommandSource] =
    currentCommand match {
      case Some(x) => x.source
      case _       => None
    }
}

trait Identity {
  override final def hashCode = super.hashCode
  override final def equals(a: Any) = super.equals(a)
  override final def toString = super.toString
}

/** Convenience methods for State transformations and operations. */
trait StateOps {
  def process(f: (Exec, State) => State): State

  /** Schedules `commands` to be run before any remaining commands.*/
  def :::(newCommands: List[String]): State

  /** Schedules `commands` to be run before any remaining commands.*/
  def ++:(newCommands: List[Exec]): State

  /** Schedules `command` to be run before any remaining commands.*/
  def ::(command: String): State

  /** Schedules `command` to be run before any remaining commands.*/
  def +:(command: Exec): State

  /** Sets the next command processing action to be to continue processing the next command.*/
  def continue: State

  /**
   * Reboots sbt.  A reboot restarts execution from the entry point of the launcher.
   * A reboot is designed to be as close as possible to actually restarting the JVM without actually doing so.
   * Because the JVM is not restarted, JVM exit hooks are not run.
   * State.exitHooks should be used instead and those will be run before rebooting.
   * If `full` is true, the boot directory is deleted before starting again.
   * This command is currently implemented to not return, but may be implemented in the future to only reboot at the next command processing step.
   */
  def reboot(full: Boolean): State

  /**
   * Reboots sbt.  A reboot restarts execution from the entry point of the launcher.
   * A reboot is designed to be as close as possible to actually restarting the JVM without actually doing so.
   * Because the JVM is not restarted, JVM exit hooks are not run.
   * State.exitHooks should be used instead and those will be run before rebooting.
   * If `full` is true, the boot directory is deleted before starting again.
   * If `currentOnly` is true, the artifacts for the current sbt version is deleted.
   * This command is currently implemented to not return, but may be implemented in the future to only reboot at the next command processing step.
   */
  private[sbt] def reboot(full: Boolean, currentOnly: Boolean): State

  /** Sets the next command processing action to do.*/
  def setNext(n: State.Next): State

  /**
   * Restarts sbt without dropping loaded Scala classes.  It is a shallower restart than `reboot`.
   * This method takes a snapshot of the remaining commands and will resume executing those commands after reload.
   * This means that any commands added to this State will be dropped.
   */
  def reload: State

  /** Sets the next command processing action to be to rotate the global log and continue executing commands.*/
  def clearGlobalLog: State

  /** Sets the next command processing action to be to keep the previous log and continue executing commands.  */
  def keepLastLog: State

  /** Sets the next command processing action to be to exit with a zero exit code if `ok` is true and a nonzero exit code if `ok` if false.*/
  def exit(ok: Boolean): State

  /** Marks the currently executing command as failing.  This triggers failure handling by the command processor.  See also `State.onFailure`*/
  def fail: State

  /**
   * Marks the currently executing command as failing due to the given exception.
   * This displays the error appropriately and triggers failure handling by the command processor.
   * Note that this does not throw an exception and returns normally.
   * It is only once control is returned to the command processor that failure handling at the command level occurs.
   */
  def handleError(t: Throwable): State

  /** Registers `newCommands` as available commands. */
  def ++(newCommands: Seq[Command]): State

  /** Registers `newCommand` as an available command. */
  def +(newCommand: Command): State

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

  /** An advisory flag that is `true` if this application will execute commands based on user input.*/
  def interactive: Boolean

  /** Changes the advisory `interactive` flag. */
  def setInteractive(flag: Boolean): State

  /** Get the class loader cache for the application.*/
  def classLoaderCache: ClassLoaderCache

  /** Create and register a class loader cache.  This should be called once at the application entry-point.*/
  def initializeClassLoaderCache: State
}

object State {

  /** Indicates where command execution should resume after a failure.*/
  val FailureWall = BasicCommandStrings.FailureWall

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

  /**
   * Provides a list of recently executed commands.  The commands are stored as processed instead of as entered by the user.
   * @param executed the list of the most recently executed commands, with the most recent command first.
   * @param maxSize the maximum number of commands to keep, or 0 to keep an unlimited number.
   */
  final class History private[State] (val executed: Seq[Exec], val maxSize: Int) {

    /** Adds `command` as the most recently executed command.*/
    def ::(command: Exec): History = {
      val prependTo =
        if (maxSize > 0 && executed.size >= maxSize) executed.take(maxSize - 1) else executed
      new History(command +: prependTo, maxSize)
    }

    /** Changes the maximum number of commands kept, adjusting the current history if necessary.*/
    def setMaxSize(size: Int): History =
      new History(if (size <= 0) executed else executed.take(size), size)
    def currentOption: Option[Exec] = executed.headOption
    def previous: Option[Exec] = executed.drop(1).headOption
  }

  /** Constructs an empty command History with a default, finite command limit.*/
  def newHistory = new History(Vector.empty, HistoryCommands.MaxLines)

  def defaultReload(state: State): Reboot = {
    val app = state.configuration.provider
    new Reboot(
      app.scalaProvider.version,
      state.remainingCommands map { case e: Exec => e.commandLine },
      app.id,
      state.configuration.baseDirectory
    )
  }

  /** Provides operations and transformations on State. */
  implicit def stateOps(s: State): StateOps = new StateOps {
    def process(f: (Exec, State) => State): State = {
      def runCmd(cmd: Exec, remainingCommands: List[Exec]) = {
        log.debug(s"> $cmd")
        f(cmd,
          s.copy(remainingCommands = remainingCommands,
                 currentCommand = Some(cmd),
                 history = cmd :: s.history))
      }
      s.remainingCommands match {
        case List()           => exit(true)
        case List(x, xs @ _*) => runCmd(x, xs.toList)
      }
    }
    def :::(newCommands: List[String]): State = ++:(newCommands map { Exec(_, s.source) })
    def ++:(newCommands: List[Exec]): State =
      s.copy(remainingCommands = newCommands ::: s.remainingCommands)
    def ::(command: String): State = +:(Exec(command, s.source))
    def +:(command: Exec): State = (command :: Nil) ++: this
    def ++(newCommands: Seq[Command]): State =
      s.copy(definedCommands = (s.definedCommands ++ newCommands).distinct)
    def +(newCommand: Command): State = this ++ (newCommand :: Nil)
    def baseDir: File = s.configuration.baseDirectory
    def setNext(n: Next) = s.copy(next = n)
    def continue = setNext(Continue)

    /** Implementation of reboot. */
    def reboot(full: Boolean): State = reboot(full, false)

    /** Implementation of reboot. */
    private[sbt] def reboot(full: Boolean, currentOnly: Boolean): State = {
      runExitHooks()
      val rs = s.remainingCommands map { case e: Exec => e.commandLine }
      if (currentOnly) throw new RebootCurrent(rs)
      else throw new xsbti.FullReload(rs.toArray, full)
    }

    def reload = runExitHooks().setNext(new Return(defaultReload(s)))
    def clearGlobalLog = setNext(ClearGlobalLog)
    def keepLastLog = setNext(KeepLastLog)
    def exit(ok: Boolean) = runExitHooks().setNext(new Return(Exit(if (ok) 0 else 1)))
    def get[T](key: AttributeKey[T]) = s.attributes get key
    def put[T](key: AttributeKey[T], value: T) = s.copy(attributes = s.attributes.put(key, value))
    def update[T](key: AttributeKey[T])(f: Option[T] => T): State = put(key, f(get(key)))
    def has(key: AttributeKey[_]) = s.attributes contains key
    def remove(key: AttributeKey[_]) = s.copy(attributes = s.attributes remove key)
    def log = s.globalLogging.full
    def handleError(t: Throwable): State = handleException(t, s, log)
    def fail = {
      import BasicCommandStrings.Compat.{ FailureWall => CompatFailureWall }
      val remaining =
        s.remainingCommands.dropWhile(c =>
          c.commandLine != FailureWall && c.commandLine != CompatFailureWall)
      if (remaining.isEmpty)
        applyOnFailure(s, Nil, exit(ok = false))
      else
        applyOnFailure(s, remaining, s.copy(remainingCommands = remaining))
    }
    private[this] def applyOnFailure(s: State, remaining: List[Exec], noHandler: => State): State =
      s.onFailure match {
        case Some(c) => s.copy(remainingCommands = c +: remaining, onFailure = None)
        case None    => noHandler
      }

    def addExitHook(act: => Unit): State =
      s.copy(exitHooks = s.exitHooks + ExitHook(act))
    def runExitHooks(): State = {
      ExitHooks.runExitHooks(s.exitHooks.toSeq)
      s.copy(exitHooks = Set.empty)
    }
    def locked[T](file: File)(t: => T): T =
      s.configuration.provider.scalaProvider.launcher.globalLock.apply(file, new Callable[T] {
        def call = t
      })

    def interactive = getBoolean(s, BasicKeys.interactive, false)
    def setInteractive(i: Boolean) = s.put(BasicKeys.interactive, i)

    def classLoaderCache: ClassLoaderCache =
      s get BasicKeys.classLoaderCache getOrElse newClassLoaderCache
    def initializeClassLoaderCache = s.put(BasicKeys.classLoaderCache, newClassLoaderCache)
    private[this] def newClassLoaderCache =
      new ClassLoaderCache(s.configuration.provider.scalaProvider.launcher.topLoader)
  }

  import ExceptionCategory._

  private[sbt] def handleException(t: Throwable, s: State, log: Logger): State = {
    ExceptionCategory(t) match {
      case AlreadyHandled => ()
      case m: MessageOnly => log.error(m.message)
      case f: Full        => logFullException(f.exception, log)
    }
    s.fail
  }
  private[sbt] def logFullException(e: Throwable, log: Logger): Unit = {
    log.trace(e)
    log.error(ErrorHandling reducedToString e)
    log.error("Use 'last' for the full log.")
  }
  private[sbt] def getBoolean(s: State, key: AttributeKey[Boolean], default: Boolean): Boolean =
    s.get(key) getOrElse default
}

private[sbt] final class RebootCurrent(val arguments: List[String]) extends RuntimeException
