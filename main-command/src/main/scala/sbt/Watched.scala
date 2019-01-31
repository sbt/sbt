/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File, InputStream }
import java.nio.file.FileSystems

import sbt.BasicCommandStrings.ContinuousExecutePrefix
import sbt.internal.LabeledFunctions._
import sbt.internal.{ FileAttributes, LegacyWatched }
import sbt.internal.io.{ EventMonitor, Source, WatchState }
import sbt.internal.util.Types.const
import sbt.internal.util.complete.DefaultParsers._
import sbt.internal.util.complete.Parser
import sbt.internal.util.{ AttributeKey, JLine, Util }
import sbt.io.FileEventMonitor.{ Creation, Deletion, Event, Update }
import sbt.io._
import sbt.util.{ Level, Logger }

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Properties
import scala.util.control.NonFatal

@deprecated("Watched is no longer used to implement continuous execution", "1.3.0")
trait Watched {

  /** The files watched when an action is run with a proceeding ~ */
  def watchSources(@deprecated("unused", "") s: State): Seq[Watched.WatchSource] = Nil
  def terminateWatch(key: Int): Boolean = Watched.isEnter(key)

  /**
   * The time in milliseconds between checking for changes.  The actual time between the last change
   * made to a file and the execution time is between `pollInterval` and `pollInterval*2`.
   */
  def pollInterval: FiniteDuration = Watched.PollDelay

  /**
   * The duration for which the EventMonitor while ignore file events after a file triggers
   * a new build.
   */
  def antiEntropy: FiniteDuration = Watched.AntiEntropy

  /** The message to show when triggered execution waits for sources to change.*/
  private[sbt] def watchingMessage(s: WatchState): String = Watched.defaultWatchingMessage(s)

  /** The message to show before an action is run. */
  private[sbt] def triggeredMessage(s: WatchState): String = Watched.defaultTriggeredMessage(s)

  /** The `WatchService` to use to monitor the file system. */
  private[sbt] def watchService(): WatchService = Watched.createWatchService()
}

object Watched {

  /**
   * This trait is used to communicate what the watch should do next at various points in time. It
   * is heavily linked to a number of callbacks in [[WatchConfig]]. For example, when the event
   * monitor detects a changed source we expect [[WatchConfig.onWatchEvent]] to return [[Trigger]].
   */
  sealed trait Action

  /**
   * Provides a default Ordering for actions. Lower values correspond to higher priority actions.
   * [[CancelWatch]] is higher priority than [[ContinueWatch]].
   */
  object Action {
    implicit object ordering extends Ordering[Action] {
      override def compare(left: Action, right: Action): Int = (left, right) match {
        case (a: ContinueWatch, b: ContinueWatch) => ContinueWatch.ordering.compare(a, b)
        case (_: ContinueWatch, _: CancelWatch)   => 1
        case (a: CancelWatch, b: CancelWatch)     => CancelWatch.ordering.compare(a, b)
        case (_: CancelWatch, _: ContinueWatch)   => -1
      }
    }
  }

  /**
   * Action that indicates that the watch should stop.
   */
  sealed trait CancelWatch extends Action

  /**
   * Action that does not terminate the watch but might trigger a build.
   */
  sealed trait ContinueWatch extends Action

  /**
   * Provides a default Ordering for classes extending [[ContinueWatch]]. [[Trigger]] is higher
   * priority than [[Ignore]].
   */
  object ContinueWatch {

    /**
     * A default [[Ordering]] for [[ContinueWatch]]. [[Trigger]] is higher priority than [[Ignore]].
     */
    implicit object ordering extends Ordering[ContinueWatch] {
      override def compare(left: ContinueWatch, right: ContinueWatch): Int = left match {
        case Ignore  => if (right == Ignore) 0 else 1
        case Trigger => if (right == Trigger) 0 else -1
      }
    }
  }

  /**
   * Action that indicates that the watch should stop.
   */
  case object CancelWatch extends CancelWatch {

    /**
     * A default [[Ordering]] for [[ContinueWatch]]. The priority of each type of [[CancelWatch]]
     * is reflected by the ordering of the case statements in the [[ordering.compare]] method,
     * e.g. [[Custom]] is higher priority than [[HandleError]].
     */
    implicit object ordering extends Ordering[CancelWatch] {
      override def compare(left: CancelWatch, right: CancelWatch): Int = left match {
        // Note that a negative return value means the left CancelWatch is preferred to the right
        // CancelWatch while the inverse is true for a positive return value. This logic could
        // likely be simplified, but the pattern matching approach makes it very clear what happens
        // for each type of Action.
        case _: Custom =>
          right match {
            case _: Custom => 0
            case _         => -1
          }
        case _: HandleError =>
          right match {
            case _: Custom      => 1
            case _: HandleError => 0
            case _              => -1
          }
        case _: Run =>
          right match {
            case _: Run               => 0
            case CancelWatch | Reload => -1
            case _                    => 1
          }
        case CancelWatch =>
          right match {
            case CancelWatch => 0
            case Reload      => -1
            case _           => 1
          }
        case Reload => if (right == Reload) 0 else 1
      }
    }
  }

  /**
   * Action that indicates that an error has occurred. The watch will be terminated when this action
   * is produced.
   */
  final class HandleError(val throwable: Throwable) extends CancelWatch {
    override def equals(o: Any): Boolean = o match {
      case that: HandleError => this.throwable == that.throwable
      case _                 => false
    }
    override def hashCode: Int = throwable.hashCode
    override def toString: String = s"HandleError($throwable)"
  }

  /**
   * Action that indicates that the watch should continue as though nothing happened. This may be
   * because, for example, no user input was yet available.
   */
  case object Ignore extends ContinueWatch

  /**
   * Action that indicates that the watch should pause while the build is reloaded. This is used to
   * automatically reload the project when the build files (e.g. build.sbt) are changed.
   */
  case object Reload extends CancelWatch

  /**
   * Action that indicates that we should exit and run the provided command.
   * @param commands the commands to run after we exit the watch
   */
  final class Run(val commands: String*) extends CancelWatch {
    override def toString: String = s"Run(${commands.mkString(", ")})"
  }
  // For now leave this private in case this isn't the best unapply type signature since it can't
  // be evolved in a binary compatible way.
  private object Run {
    def unapply(r: Run): Option[List[Exec]] = Some(r.commands.toList.map(Exec(_, None)))
  }

  /**
   * Action that indicates that the watch process should re-run the command.
   */
  case object Trigger extends ContinueWatch

  /**
   * A user defined Action. It is not sealed so that the user can create custom instances. If any
   * of the [[Watched.watch]] callbacks return [[Custom]], then watch will terminate.
   */
  trait Custom extends CancelWatch

  @deprecated("WatchSource is replaced by sbt.io.Glob", "1.3.0")
  type WatchSource = Source
  private[sbt] type OnTermination = (Action, String, State) => State
  private[sbt] type OnEnter = () => Unit
  def terminateWatch(key: Int): Boolean = Watched.isEnter(key)

  /**
   * A constant function that returns [[Trigger]].
   */
  final val trigger: (Int, Event[FileAttributes]) => Watched.Action = {
    (_: Int, _: Event[FileAttributes]) =>
      Trigger
  }.label("Watched.trigger")

  def ifChanged(action: Action): (Int, Event[FileAttributes]) => Watched.Action =
    (_: Int, event: Event[FileAttributes]) =>
      event match {
        case Update(prev, cur, _) if prev.value != cur.value => action
        case _: Creation[_] | _: Deletion[_]                 => action
        case _                                               => Ignore
    }

  private[this] val options =
    if (Util.isWindows)
      "press 'enter' to return to the shell or the following keys followed by 'enter': 'r' to" +
        " re-run the command, 'x' to exit sbt"
    else "press 'r' to re-run the command, 'x' to exit sbt or 'enter' to return to the shell"
  private def waitMessage(project: String): String =
    s"Waiting for source changes$project... (press enter to interrupt$options)"

  /**
   * The minimum delay between build triggers for the same file. If the file is detected
   * to have changed within this period from the last build trigger, the event will be discarded.
   */
  final val defaultAntiEntropy: FiniteDuration = 500.milliseconds

  /**
   * The duration in wall clock time for which a FileEventMonitor will retain anti-entropy
   * events for files. This is an implementation detail of the FileEventMonitor. It should
   * hopefully not need to be set by the users. It is needed because when a task takes a long time
   * to run, it is possible that events will be detected for the file that triggered the build that
   * occur within the anti-entropy period. We still allow it to be configured to limit the memory
   * usage of the FileEventMonitor (but this is somewhat unlikely to be a problem).
   */
  final val defaultAntiEntropyRetentionPeriod: FiniteDuration = 10.minutes

  /**
   * The duration for which we delay triggering when a file is deleted. This is needed because
   * many programs implement save as a file move of a temporary file onto the target file.
   * Depending on how the move is implemented, this may be detected as a deletion immediately
   * followed by a creation. If we trigger immediately on delete, we may, for example, try to
   * compile before all of the source files are actually available. The longer this value is set,
   * the less likely we are to spuriously trigger a build before all files are available, but
   * the longer it will take to trigger a build when the file is actually deleted and not renamed.
   */
  final val defaultDeletionQuarantinePeriod: FiniteDuration = 50.milliseconds

  /**
   * Converts user input to an Action with the following rules:
   * 1) on all platforms, new lines exit the watch
   * 2) on posix platforms, 'r' or 'R' will trigger a build
   * 3) on posix platforms, 's' or 'S' will exit the watch and run the shell command. This is to
   *    support the case where the user starts sbt in a continuous mode but wants to return to
   *    the shell without having to restart sbt.
   */
  final val defaultInputParser: Parser[Action] = {
    def posixOnly(legal: String, action: Action): Parser[Action] =
      if (!Util.isWindows) chars(legal) ^^^ action
      else Parser.invalid(Seq("Can't use jline for individual character entry on windows."))
    val rebuildParser: Parser[Action] = posixOnly(legal = "rR", Trigger)
    val shellParser: Parser[Action] = posixOnly(legal = "sS", new Run("shell"))
    val cancelParser: Parser[Action] = chars(legal = "\n\r") ^^^ CancelWatch
    shellParser | rebuildParser | cancelParser
  }

  /**
   * A function that prints out the current iteration count and gives instructions for exiting
   * or triggering the build.
   */
  val defaultStartWatch: Int => Option[String] =
    ((count: Int) => Some(s"$count. ${waitMessage("")}")).label("Watched.defaultStartWatch")

  /**
   * Default no-op callback.
   */
  val defaultOnEnter: () => Unit = () => {}

  private[sbt] val defaultCommandOnTermination: (Action, String, Int, State) => State =
    onTerminationImpl(ContinuousExecutePrefix).label("Watched.defaultCommandOnTermination")
  private[sbt] val defaultTaskOnTermination: (Action, String, Int, State) => State =
    onTerminationImpl("watch", ContinuousExecutePrefix).label("Watched.defaultTaskOnTermination")

  /**
   * Default handler to transform the state when the watch terminates. When the [[Watched.Action]]
   * is [[Reload]], the handler will prepend the original command (prefixed by ~) to the
   * [[State.remainingCommands]] and then invoke the [[StateOps.reload]] method. When the
   * [[Watched.Action]] is [[HandleError]], the handler returns the result of [[StateOps.fail]].
   * When the [[Watched.Action]] is [[Watched.Run]], we add the commands specified by
   * [[Watched.Run.commands]] to the stat's remaining commands. Otherwise the original state is
   * returned.
   */
  private def onTerminationImpl(
      watchPrefixes: String*
  ): (Action, String, Int, State) => State = { (action, command, count, state) =>
    val prefix = watchPrefixes.head
    val rc = state.remainingCommands
      .filterNot(c => watchPrefixes.exists(c.commandLine.trim.startsWith))
    action match {
      case Run(commands) => state.copy(remainingCommands = commands ++ rc)
      case Reload =>
        state.copy(remainingCommands = "reload".toExec :: s"$prefix $count $command".toExec :: rc)
      case _: HandleError => state.copy(remainingCommands = rc).fail
      case _              => state.copy(remainingCommands = rc)
    }
  }

  /**
   * A constant function that always returns [[None]]. When
   * `Keys.watchTriggeredMessage := Watched.defaultOnTriggerMessage`, then nothing is logged when
   * a build is triggered.
   */
  final val defaultOnTriggerMessage: (Int, Event[FileAttributes]) => Option[String] =
    ((_: Int, _: Event[FileAttributes]) => None).label("Watched.defaultOnTriggerMessage")

  /**
   * The minimum delay between file system polling when a [[PollingWatchService]] is used.
   */
  final val defaultPollInterval: FiniteDuration = 500.milliseconds

  /**
   * A constant function that returns an Option wrapped string that clears the screen when
   * written to stdout.
   */
  final val clearOnTrigger: Int => Option[String] =
    ((_: Int) => Some(clearScreen)).label("Watched.clearOnTrigger")
  def clearScreen: String = "\u001b[2J\u001b[0;0H"

  @deprecated("WatchSource has been replaced by sbt.io.Glob", "1.3.0")
  object WatchSource {

    /**
     * Creates a new `WatchSource` for watching files, with the given filters.
     *
     * @param base          The base directory from which to include files.
     * @param includeFilter Choose what children of `base` to include.
     * @param excludeFilter Choose what children of `base` to exclude.
     * @return An instance of `Source`.
     */
    def apply(base: File, includeFilter: FileFilter, excludeFilter: FileFilter): Source =
      new Source(base, includeFilter, excludeFilter)

    /**
     * Creates a new `WatchSource` for watching files.
     *
     * @param base          The base directory from which to include files.
     * @return An instance of `Source`.
     */
    def apply(base: File): Source = apply(base, AllPassFilter, NothingFilter)

  }

  private type RunCommand = () => State
  private type NextAction = () => Watched.Action
  private[sbt] type Monitor = FileEventMonitor[FileAttributes]

  /**
   * Runs a task and then blocks until the task is ready to run again or we no longer wish to
   * block execution.
   *
   * @param task the aggregated task to run with each iteration
   * @param onStart function to be invoked before we start polling for events
   * @param nextAction function that returns the next state transition [[Watched.Action]].
   * @return the exit [[Watched.Action]] that can be used to potentially modify the build state and
   *         the count of the number of iterations that were run. If
   */
  def watch(task: () => Unit, onStart: NextAction, nextAction: NextAction): Watched.Action = {
    def safeNextAction(delegate: NextAction): Watched.Action =
      try delegate()
      catch { case NonFatal(t) => new HandleError(t) }
    @tailrec def next(): Watched.Action = safeNextAction(nextAction) match {
      // This should never return Ignore due to this condition.
      case Ignore => next()
      case action => action
    }
    @tailrec def impl(): Watched.Action = {
      task()
      safeNextAction(onStart) match {
        case Ignore =>
          next() match {
            case Trigger => impl()
            case action  => action
          }
        case Trigger => impl()
        case a       => a
      }
    }
    try impl()
    catch { case NonFatal(t) => new HandleError(t) }
  }

  private[sbt] object NullLogger extends Logger {
    override def trace(t: => Throwable): Unit = {}
    override def success(message: => String): Unit = {}
    override def log(level: Level.Value, message: => String): Unit = {}
  }

  /**
   * Traverse all of the events and find the one for which we give the highest
   * weight. Within the [[Action]] hierarchy:
   * [[Custom]] > [[HandleError]] > [[Run]] > [[CancelWatch]] > [[Reload]] > [[Trigger]] > [[Ignore]]
   * the first event of each kind is returned so long as there are no higher priority events
   * in the collection. For example, if there are multiple events that all return [[Trigger]], then
   * the first one is returned. If, on the other hand, one of the events returns [[Reload]],
   * then that event "wins" and the [[Reload]] action is returned with the [[Event[FileAttributes]]]
   * that triggered it.
   *
   * @param events the ([[Action]], [[Event[FileAttributes]]]) pairs
   * @return the ([[Action]], [[Event[FileAttributes]]]) pair with highest weight if the input events
   *         are non empty.
   */
  @inline
  private[sbt] def aggregate(
      events: Seq[(Action, Event[FileAttributes])]
  ): Option[(Action, Event[FileAttributes])] =
    if (events.isEmpty) None else Some(events.minBy(_._1))

  private implicit class StringToExec(val s: String) extends AnyVal {
    def toExec: Exec = Exec(s, None)
  }

  private[sbt] def withCharBufferedStdIn[R](f: InputStream => R): R =
    if (!Util.isWindows) JLine.usingTerminal { terminal =>
      terminal.init()
      val in = terminal.wrapInIfNeeded(System.in)
      try {
        f(in)
      } finally {
        terminal.reset()
      }
    } else
      f(System.in)

  private[sbt] val newWatchService: () => WatchService =
    (() => createWatchService()).label("Watched.newWatchService")
  def createWatchService(pollDelay: FiniteDuration): WatchService = {
    def closeWatch = new MacOSXWatchService()
    sys.props.get("sbt.watch.mode") match {
      case Some("polling") =>
        new PollingWatchService(pollDelay)
      case Some("nio") =>
        FileSystems.getDefault.newWatchService()
      case Some("closewatch")    => closeWatch
      case _ if Properties.isMac => closeWatch
      case _ =>
        FileSystems.getDefault.newWatchService()
    }
  }

  @deprecated("This is no longer used by continuous builds.", "1.3.0")
  def printIfDefined(msg: String): Unit = if (!msg.isEmpty) System.out.println(msg)
  @deprecated("This is no longer used by continuous builds.", "1.3.0")
  def isEnter(key: Int): Boolean = key == 10 || key == 13
  @deprecated("Replaced by defaultPollInterval", "1.3.0")
  val PollDelay: FiniteDuration = 500.milliseconds
  @deprecated("Replaced by defaultAntiEntropy", "1.3.0")
  val AntiEntropy: FiniteDuration = 40.milliseconds
  @deprecated("Use the version that explicitly takes the poll delay", "1.3.0")
  def createWatchService(): WatchService = createWatchService(PollDelay)

  @deprecated("Replaced by Watched.command", "1.3.0")
  def executeContinuously(watched: Watched, s: State, next: String, repeat: String): State =
    LegacyWatched.executeContinuously(watched, s, next, repeat)

  // Deprecated apis below
  @deprecated("unused", "1.3.0")
  def projectWatchingMessage(projectId: String): WatchState => String =
    ((ws: WatchState) => projectOnWatchMessage(projectId)(ws.count).get)
      .label("Watched.projectWatchingMessage")
  @deprecated("unused", "1.3.0")
  def projectOnWatchMessage(project: String): Int => Option[String] =
    ((count: Int) => Some(s"$count. ${waitMessage(s" in project $project")}"))
      .label("Watched.projectOnWatchMessage")

  @deprecated("This method is not used and may be removed in a future version of sbt", "1.3.0")
  private[this] class AWatched extends Watched

  @deprecated("This method is not used and may be removed in a future version of sbt", "1.3.0")
  def multi(base: Watched, paths: Seq[Watched]): Watched =
    new AWatched {
      override def watchSources(s: State): Seq[Watched.WatchSource] =
        (base.watchSources(s) /: paths)(_ ++ _.watchSources(s))
      override def terminateWatch(key: Int): Boolean = base.terminateWatch(key)
      override val pollInterval: FiniteDuration = (base +: paths).map(_.pollInterval).min
      override val antiEntropy: FiniteDuration = (base +: paths).map(_.antiEntropy).min
      override def watchingMessage(s: WatchState): String = base.watchingMessage(s)
      override def triggeredMessage(s: WatchState): String = base.triggeredMessage(s)
    }
  @deprecated("This method is not used and may be removed in a future version of sbt", "1.3.0")
  def empty: Watched = new AWatched

  @deprecated("ContinuousEventMonitor attribute is not used by Watched.command", "1.3.0")
  val ContinuousEventMonitor =
    AttributeKey[EventMonitor](
      "watch event monitor",
      "Internal: maintains watch state and monitor threads."
    )
  @deprecated("Superseded by ContinuousEventMonitor", "1.3.0")
  val ContinuousState =
    AttributeKey[WatchState]("watch state", "Internal: tracks state for continuous execution.")

  @deprecated("Superseded by ContinuousEventMonitor", "1.3.0")
  val ContinuousWatchService =
    AttributeKey[WatchService](
      "watch service",
      "Internal: tracks watch service for continuous execution."
    )
  @deprecated("No longer used for continuous execution", "1.3.0")
  val Configuration =
    AttributeKey[Watched]("watched-configuration", "Configures continuous execution.")

  @deprecated("Use defaultStartWatch in conjunction with the watchStartMessage key", "1.3.0")
  val defaultWatchingMessage: WatchState => String =
    ((ws: WatchState) => defaultStartWatch(ws.count).get).label("Watched.defaultWatchingMessage")
  @deprecated(
    "Use defaultOnTriggerMessage in conjunction with the watchTriggeredMessage key",
    "1.3.0"
  )
  val defaultTriggeredMessage: WatchState => String =
    const("").label("Watched.defaultTriggeredMessage")
  @deprecated("Use clearOnTrigger in conjunction with the watchTriggeredMessage key", "1.3.0")
  val clearWhenTriggered: WatchState => String =
    const(clearScreen).label("Watched.clearWhenTriggered")
}
