/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.net.{ URL, URLClassLoader }
import java.util.concurrent.Callable

import sbt.internal.classpath.ClassLoaderCache
import sbt.internal.inc.classpath.{ ClassLoaderCache => IncClassLoaderCache }
import sbt.internal.util.complete.{ HistoryCommands, Parser }
import sbt.internal.util._
import sbt.util.Logger
import BasicCommandStrings.{
  CompleteExec,
  MapExec,
  PopOnFailure,
  ReportResult,
  StartServer,
  StashOnFailure,
  networkExecPrefix,
}

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
  private[sbt] lazy val (multiCommands, nonMultiCommands) =
    definedCommands.partition(_.nameOption.contains(BasicCommandStrings.Multi))
  private[sbt] lazy val nonMultiParser = Command.combine(nonMultiCommands)(this)
  lazy val combinedParser: Parser[() => State] =
    multiCommands.headOption match {
      case Some(multi) => multi.parser(this) | nonMultiParser
      case _           => nonMultiParser
    }

  def source: Option[CommandSource] =
    currentCommand match {
      case Some(x) => x.source
      case _       => None
    }
}

/**
 * Data structure extracted form the State Machine for safe observability purposes.
 *
 * @param currentExecId provide the execId extracted from the original State.
 * @param combinedParser the parser extracted from the original State.
 */
@deprecated("unused", "1.4.2")
private[sbt] final case class SafeState(
    currentExecId: Option[String],
    combinedParser: Parser[() => sbt.State]
)

@deprecated("unused", "1.4.2")
private[sbt] object SafeState {
  @deprecated("use StandardMain.exchange.withState", "1.4.2")
  def apply(s: State) = {
    new SafeState(
      currentExecId = s.currentCommand.map(_.execId).flatten,
      combinedParser = s.combinedParser
    )
  }
}

trait Identity {
  override final def hashCode = super.hashCode
  override final def equals(a: Any) = super.equals(a)
  override final def toString = super.toString
}

/** Convenience methods for State transformations and operations. */
trait StateOps extends Any {
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
  def classLoaderCache: IncClassLoaderCache

  /** Create and register a class loader cache.  This should be called once at the application entry-point.*/
  def initializeClassLoaderCache: State
}

object State {
  private class UncloseableURLLoader(cp: Seq[File], parent: ClassLoader)
      extends URLClassLoader(Array.empty, parent) {
    override def getURLs: Array[URL] = cp.map(_.toURI.toURL).toArray
  }

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
   *
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

  @deprecated("Import State._ or State.StateOpsImpl to access state extension methods", "1.3.0")
  def stateOps(s: State): StateOps = new StateOpsImpl(s)

  /** Provides operations and transformations on State. */
  implicit class StateOpsImpl(val s: State) extends AnyVal with StateOps {
    def process(f: (Exec, State) => State): State = {
      def runCmd(cmd: Exec, remainingCommands: List[Exec]) = {
        log.debug(s"> $cmd")
        val s1 = s.copy(
          remainingCommands = remainingCommands,
          currentCommand = Some(cmd),
          history = cmd :: s.history,
        )
        f(cmd, s1)
      }
      s.remainingCommands match {
        case Nil => exit(true)
        case x :: xs =>
          (x.execId, x.source) match {
            /*
             * If the command is coming from a network source, it might be a multi-command. To handle
             * that, we need to give the command a new exec id and wrap some commands around the
             * actual command that are used to report it. To make this work, we add a map of exec
             * results as well as a mapping of exec ids to the exec id that spawned the exec.
             * We add a command that fills the result map for the original exec. If the command fails,
             * that map filling command (called sbtCompleteExec) is skipped so the map is never filled
             * for the original event. The report command (called sbtReportResult) checks the result
             * map and, if it finds an entry, it succeeds and removes the entry. Otherwise it fails.
             * The exec for the report command is given the original exec id so the result reported
             * to the client will be the result of the report command (which should correspond to
             * the result of the underlying multi-command, which succeeds only if all of the commands
             * succeed)
             *
             */
            case (Some(id), Some(s))
                if s.channelName.startsWith("network") &&
                  !x.commandLine.startsWith(ReportResult) &&
                  !x.commandLine.startsWith(networkExecPrefix) &&
                  !id.startsWith(networkExecPrefix) =>
              val newID = networkExecPrefix + Exec.newExecId
              val cmd = x.withExecId(newID)
              val map = Exec(s"$MapExec $id $newID", None, x.source)
              val complete = Exec(s"$CompleteExec $id", None, x.source)
              val report = Exec(s"$ReportResult $id", Some(id), x.source)
              val stash = Exec(StashOnFailure, None, x.source)
              val failureWall = Exec(FailureWall, None, x.source)
              val pop = Exec(PopOnFailure, None, x.source)
              val remaining = map :: cmd :: complete :: failureWall :: pop :: report :: xs
              runCmd(stash, remaining)
            case _ => runCmd(x, xs)
          }
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
      val remaining: List[String] = s.remainingCommands.map(_.commandLine)
      val fullRemaining = s.source match {
        case Some(s) if s.channelName.startsWith("network") =>
          StartServer :: remaining.dropWhile(!_.startsWith(ReportResult)).tail ::: "shell" :: Nil
        case _ => remaining
      }
      if (currentOnly) throw new RebootCurrent(fullRemaining)
      else throw new xsbti.FullReload(fullRemaining.toArray, full)
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
      val remaining = s.remainingCommands.dropWhile(c => c.commandLine != FailureWall)
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

    def classLoaderCache: IncClassLoaderCache =
      s get BasicKeys.classLoaderCache getOrElse (throw new IllegalStateException(
        "Tried to get classloader cache for uninitialized state."
      ))
    private[sbt] def extendedClassLoaderCache: ClassLoaderCache =
      s get BasicKeys.extendedClassLoaderCache getOrElse (throw new IllegalStateException(
        "Tried to get extended classloader cache for uninitialized state."
      ))
    def initializeClassLoaderCache: State = {
      s.get(BasicKeys.extendedClassLoaderCache).foreach(_.close())
      val cache = newClassLoaderCache
      s.configuration.provider.scalaProvider.loader match {
        case null => // This can happen in scripted
        case fullScalaLoader =>
          val jars = s.configuration.provider.scalaProvider.jars
          val (library, rest) = jars.partition(_.getName == "scala-library.jar")
          library.toList match {
            case l @ lj :: Nil =>
              fullScalaLoader.getParent match {
                case null => // This can happen for old launchers.
                case libraryLoader =>
                  cache.cachedCustomClassloader(l, () => new UncloseableURLLoader(l, libraryLoader))
                  fullScalaLoader match {
                    case u: URLClassLoader
                        if u.getURLs
                          .filterNot(_ == lj.toURI.toURL)
                          .sameElements(rest.map(_.toURI.toURL)) =>
                      cache.cachedCustomClassloader(
                        jars.toList,
                        () => new UncloseableURLLoader(jars, fullScalaLoader)
                      )
                      ()
                    case _ =>
                  }
              }
            case _ =>
          }
      }
      s.put(BasicKeys.extendedClassLoaderCache, cache)
        .put(BasicKeys.classLoaderCache, new IncClassLoaderCache(cache))
    }
    private[this] def newClassLoaderCache =
      new ClassLoaderCache(s.configuration.provider.scalaProvider)
  }

  import ExceptionCategory._

  private[this] def handleException(t: Throwable, s: State, log: Logger): State = {
    ExceptionCategory(t) match {
      case AlreadyHandled => ()
      case m: MessageOnly => log.error(m.message)
      case f: Full        => logFullException(f.exception, log)
    }
    s.fail
  }
  private[sbt] def logFullException(e: Throwable, log: Logger): Unit = {
    e.printStackTrace(System.err)
    log.trace(e)
    log.error(ErrorHandling reducedToString e)
    log.error("Use 'last' for the full log.")
  }
  private[sbt] def getBoolean(s: State, key: AttributeKey[Boolean], default: Boolean): Boolean =
    s.get(key) getOrElse default
}

private[sbt] final class RebootCurrent(val arguments: List[String]) extends RuntimeException
