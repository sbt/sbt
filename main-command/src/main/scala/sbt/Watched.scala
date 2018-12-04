/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File, InputStream }
import java.nio.file.FileSystems

import sbt.BasicCommandStrings.{
  ContinuousExecutePrefix,
  FailureWall,
  continuousBriefHelp,
  continuousDetail
}
import sbt.BasicCommands.otherCommandParser
import sbt.internal.LabeledFunctions._
import sbt.internal.io.{ EventMonitor, Source, WatchState }
import sbt.internal.util.Types.const
import sbt.internal.util.complete.{ DefaultParsers, Parser }
import sbt.internal.util.{ AttributeKey, JLine }
import sbt.internal.{ FileCacheEntry, LegacyWatched }
import sbt.io.FileEventMonitor.{ Creation, Deletion, Event, Update }
import sbt.io._
import sbt.util.{ Level, Logger }

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Properties

@deprecated("Watched is no longer used to implement continuous execution", "1.3.0")
trait Watched {

  /** The files watched when an action is run with a proceeding ~ */
  def watchSources(@deprecated("unused", "") s: State): Seq[Watched.WatchSource] = Nil
  def terminateWatch(key: Int): Boolean = Watched.isEnter(key)

  /**
   * The time in milliseconds between checking for changes.  The actual time between the last change made to a file and the
   * execution time is between `pollInterval` and `pollInterval*2`.
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
   * is heavily linked to a number of callbacks in [[WatchConfig]]. For example, when the
   * sbt.io.FileEventMonitor created by [[FileTreeViewConfig.newMonitor]] detects a changed source
   * file, then we expect [[WatchConfig.onWatchEvent]] to return [[Trigger]].
   */
  sealed trait Action

  /**
   * Action that indicates that the watch should stop.
   */
  case object CancelWatch extends Action

  /**
   * Action that indicates that an error has occurred. The watch will be terminated when this action
   * is produced.
   */
  case object HandleError extends Action

  /**
   * Action that indicates that the watch should continue as though nothing happened. This may be
   * because, for example, no user input was yet available in [[WatchConfig.handleInput]].
   */
  case object Ignore extends Action

  /**
   * Action that indicates that the watch should pause while the build is reloaded. This is used to
   * automatically reload the project when the build files (e.g. build.sbt) are changed.
   */
  case object Reload extends Action

  /**
   * Action that indicates that the watch process should re-run the command.
   */
  case object Trigger extends Action

  /**
   * A user defined Action. It is not sealed so that the user can create custom instances. If any
   * of the [[WatchConfig]] callbacks, e.g. [[WatchConfig.onWatchEvent]], return an instance of
   * [[Custom]], the watch will terminate.
   */
  trait Custom extends Action

  type WatchSource = Source
  def terminateWatch(key: Int): Boolean = Watched.isEnter(key)

  private[this] val isWin = Properties.isWin
  private def drain(is: InputStream): Unit = while (is.available > 0) is.read()
  private def withCharBufferedStdIn[R](f: InputStream => R): R =
    if (!isWin) JLine.usingTerminal { terminal =>
      terminal.init()
      val in = terminal.wrapInIfNeeded(System.in)
      try {
        drain(in)
        f(in)
      } finally {
        drain(in)
        terminal.reset()
      }
    } else
      try {
        drain(System.in)
        f(System.in)
      } finally drain(System.in)

  private[sbt] final val handleInput: InputStream => Action = in => {
    @tailrec
    def scanInput(): Action = {
      if (in.available > 0) {
        in.read() match {
          case key if isEnter(key)       => CancelWatch
          case key if isR(key) && !isWin => Trigger
          case key if key >= 0           => scanInput()
          case _                         => Ignore
        }
      } else {
        Ignore
      }
    }
    scanInput()
  }
  private[sbt] def onEvent(
      sources: Seq[WatchSource],
      projectSources: Seq[WatchSource]
  ): Event[FileCacheEntry] => Watched.Action =
    event =>
      if (sources.exists(_.accept(event.entry.typedPath.toPath))) Watched.Trigger
      else if (projectSources.exists(_.accept(event.entry.typedPath.toPath))) event match {
        case Update(prev, cur, _) if prev.value != cur.value => Reload
        case _: Creation[_] | _: Deletion[_]                 => Reload
        case _                                               => Ignore
      } else Ignore

  private[this] val reRun = if (isWin) "" else " or 'r' to re-run the command"
  private def waitMessage(project: String): String =
    s"Waiting for source changes$project... (press enter to interrupt$reRun)"
  val defaultStartWatch: Int => Option[String] =
    ((count: Int) => Some(s"$count. ${waitMessage("")}")).label("Watched.defaultStartWatch")
  @deprecated("Use defaultStartWatch in conjunction with the watchStartMessage key", "1.3.0")
  val defaultWatchingMessage: WatchState => String =
    ((ws: WatchState) => defaultStartWatch(ws.count).get).label("Watched.defaultWatchingMessage")
  def projectWatchingMessage(projectId: String): WatchState => String =
    ((ws: WatchState) => projectOnWatchMessage(projectId)(ws.count).get)
      .label("Watched.projectWatchingMessage")
  def projectOnWatchMessage(project: String): Int => Option[String] =
    ((count: Int) => Some(s"$count. ${waitMessage(s" in project $project")}"))
      .label("Watched.projectOnWatchMessage")

  val defaultOnTriggerMessage: Int => Option[String] =
    ((_: Int) => None).label("Watched.defaultOnTriggerMessage")
  @deprecated(
    "Use defaultOnTriggerMessage in conjunction with the watchTriggeredMessage key",
    "1.3.0"
  )
  val defaultTriggeredMessage: WatchState => String =
    const("").label("Watched.defaultTriggeredMessage")
  val clearOnTrigger: Int => Option[String] = _ => Some(clearScreen)
  @deprecated("Use clearOnTrigger in conjunction with the watchTriggeredMessage key", "1.3.0")
  val clearWhenTriggered: WatchState => String =
    const(clearScreen).label("Watched.clearWhenTriggered")
  def clearScreen: String = "\u001b[2J\u001b[0;0H"

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

  val PollDelay: FiniteDuration = 500.milliseconds
  val AntiEntropy: FiniteDuration = 40.milliseconds
  def isEnter(key: Int): Boolean = key == 10 || key == 13
  def isR(key: Int): Boolean = key == 82 || key == 114
  def printIfDefined(msg: String): Unit = if (!msg.isEmpty) System.out.println(msg)

  private type RunCommand = () => State
  private type WatchSetup = (State, String) => (State, WatchConfig, RunCommand => State)

  /**
   * Provides the '~' continuous execution command.
   * @param setup a function that provides a logger and a function from (() => State) => State.
   * @return the '~' command.
   */
  def continuous(setup: WatchSetup): Command =
    Command(ContinuousExecutePrefix, continuousBriefHelp, continuousDetail)(otherCommandParser) {
      (state, command) =>
        Watched.executeContinuously(state, command, setup)
    }

  /**
   * Default handler to transform the state when the watch terminates. When the [[Watched.Action]] is
   * [[Reload]], the handler will prepend the original command (prefixed by ~) to the
   * [[State.remainingCommands]] and then invoke the [[StateOps.reload]] method. When the
   * [[Watched.Action]] is [[HandleError]], the handler returns the result of [[StateOps.fail]]. Otherwise
   * the original state is returned.
   */
  private[sbt] val onTermination: (Action, String, State) => State = (action, command, state) =>
    action match {
      case Reload =>
        val continuousCommand = Exec(ContinuousExecutePrefix + command, None)
        state.copy(remainingCommands = continuousCommand +: state.remainingCommands).reload
      case HandleError => state.fail
      case _           => state
  }

  /**
   * Implements continuous execution. It works by first parsing the command and generating a task to
   * run with each build. It can run multiple commands that are separated by ";" in the command
   * input. If any of these commands are invalid, the watch will immediately exit.
   * @param state the initial state
   * @param command the command(s) to repeatedly apply
   * @param setup function to generate a logger and a transformation of the resultant state. The
   *              purpose of the transformation is to preserve the logging semantics that existed
   *              in the legacy version of this function in which the task would be run through
   *              MainLoop.processCommand, which is unavailable in the main-command project
   * @return the initial state if all of the input commands are valid. Otherwise, returns the
   *         initial state with the failure transformation.
   */
  private[sbt] def executeContinuously(
      state: State,
      command: String,
      setup: WatchSetup,
  ): State = withCharBufferedStdIn { in =>
    val (s0, config, newState) = setup(state, command)
    val failureCommandName = "SbtContinuousWatchOnFail"
    val onFail = Command.command(failureCommandName)(identity)
    val s = (FailureWall :: s0).copy(
      onFailure = Some(Exec(failureCommandName, None)),
      definedCommands = s0.definedCommands :+ onFail
    )
    val commands = Parser.parse(command, BasicCommands.multiParserImpl(Some(s))) match {
      case Left(_)  => command :: Nil
      case Right(c) => c
    }
    val parser = Command.combine(s.definedCommands)(s)
    val tasks = commands.foldLeft(Nil: Seq[Either[String, () => Either[Exception, Boolean]]]) {
      (t, cmd) =>
        t :+ (DefaultParsers.parse(cmd, parser) match {
          case Right(task) =>
            Right { () =>
              try {
                Right(newState(task).remainingCommands.forall(_.commandLine != failureCommandName))
              } catch { case e: Exception => Left(e) }
            }
          case Left(_) => Left(cmd)
        })
    }
    val (valid, invalid) = tasks.partition(_.isRight)
    if (invalid.isEmpty) {
      val task = () =>
        valid.foldLeft(Right(true): Either[Exception, Boolean]) {
          case (status, Right(t)) => if (status.getOrElse(true)) t() else status
          case _                  => throw new IllegalStateException("Should be unreachable")
      }
      val terminationAction = watch(in, task, config)
      config.onWatchTerminated(terminationAction, command, state)
    } else {
      val commands = invalid.flatMap(_.left.toOption).mkString("'", "', '", "'")
      config.logger.error(s"Terminating watch due to invalid command(s): $commands")
      state.fail
    }
  }

  private[sbt] def watch(
      in: InputStream,
      task: () => Either[Exception, Boolean],
      config: WatchConfig
  ): Action = {
    val logger = config.logger
    def info(msg: String): Unit = if (msg.nonEmpty) logger.info(msg)

    @tailrec
    def impl(count: Int): Action = {
      @tailrec
      def nextAction(): Action = {
        config.handleInput(in) match {
          case action @ (CancelWatch | HandleError | Reload | _: Custom) => action
          case Trigger                                                   => Trigger
          case _ =>
            val events = config.fileEventMonitor.poll(10.millis)
            val next = events match {
              case Seq()                => (Ignore, None)
              case Seq(head, tail @ _*) =>
                /*
                 * We traverse all of the events and find the one for which we give the highest
                 * weight.
                 * Custom > HandleError > CancelWatch > Reload > Trigger > Ignore
                 */
                tail.foldLeft((config.onWatchEvent(head), Some(head))) {
                  case (current @ (_: Custom, _), _) => current
                  case (current @ (action, _), event) =>
                    config.onWatchEvent(event) match {
                      case HandleError                          => (HandleError, Some(event))
                      case CancelWatch if action != HandleError => (CancelWatch, Some(event))
                      case Reload if action != HandleError && action != CancelWatch =>
                        (Reload, Some(event))
                      case Trigger if action == Ignore => (Trigger, Some(event))
                      case _                           => current
                    }
                }
            }
            // Note that nextAction should never return Ignore.
            next match {
              case (action @ (HandleError | CancelWatch | _: Custom), Some(event)) =>
                val cause =
                  if (action == HandleError) "error"
                  else if (action.isInstanceOf[Custom]) action.toString
                  else "cancellation"
                logger.debug(s"Stopping watch due to $cause from ${event.entry.typedPath.toPath}")
                action
              case (Trigger, Some(event)) =>
                logger.debug(s"Triggered by ${event.entry.typedPath.toPath}")
                config.triggeredMessage(event.entry.typedPath, count).foreach(info)
                Trigger
              case (Reload, Some(event)) =>
                logger.info(s"Reload triggered by ${event.entry.typedPath.toPath}")
                Reload
              case _ =>
                nextAction()
            }
        }
      }
      task() match {
        case Right(status) =>
          config.preWatch(count, status) match {
            case Ignore =>
              config.watchingMessage(count).foreach(info)
              nextAction() match {
                case action @ (CancelWatch | HandleError | Reload | _: Custom) => action
                case _                                                         => impl(count + 1)
              }
            case Trigger                                                   => impl(count + 1)
            case action @ (CancelWatch | HandleError | Reload | _: Custom) => action
          }
        case Left(e) =>
          logger.error(s"Terminating watch due to Unexpected error: $e")
          HandleError
      }
    }
    try impl(count = 1)
    finally config.fileEventMonitor.close()
  }

  @deprecated("Replaced by Watched.command", "1.3.0")
  def executeContinuously(watched: Watched, s: State, next: String, repeat: String): State =
    LegacyWatched.executeContinuously(watched, s, next, repeat)

  private[sbt] object NullLogger extends Logger {
    override def trace(t: => Throwable): Unit = {}
    override def success(message: => String): Unit = {}
    override def log(level: Level.Value, message: => String): Unit = {}
  }

  @deprecated("ContinuousEventMonitor attribute is not used by Watched.command", "1.3.0")
  val ContinuousEventMonitor =
    AttributeKey[EventMonitor](
      "watch event monitor",
      "Internal: maintains watch state and monitor threads."
    )
  @deprecated("Superseded by ContinuousEventMonitor", "1.1.5")
  val ContinuousState =
    AttributeKey[WatchState]("watch state", "Internal: tracks state for continuous execution.")

  @deprecated("Superseded by ContinuousEventMonitor", "1.1.5")
  val ContinuousWatchService =
    AttributeKey[WatchService](
      "watch service",
      "Internal: tracks watch service for continuous execution."
    )
  val Configuration =
    AttributeKey[Watched]("watched-configuration", "Configures continuous execution.")

  def createWatchService(): WatchService = {
    def closeWatch = new MacOSXWatchService()
    sys.props.get("sbt.watch.mode") match {
      case Some("polling") =>
        new PollingWatchService(PollDelay)
      case Some("nio") =>
        FileSystems.getDefault.newWatchService()
      case Some("closewatch")    => closeWatch
      case _ if Properties.isMac => closeWatch
      case _ =>
        FileSystems.getDefault.newWatchService()
    }
  }
}

/**
 * Provides a number of configuration options for continuous execution.
 */
trait WatchConfig {

  /**
   * A logger.
   * @return a logger
   */
  def logger: Logger

  /**
   * The sbt.io.FileEventMonitor that is used to monitor the file system.
   *
   * @return an sbt.io.FileEventMonitor instance.
   */
  def fileEventMonitor: FileEventMonitor[FileCacheEntry]

  /**
   * A function that is periodically invoked to determine whether the watch should stop or
   * trigger. Usually this will read from System.in to react to user input.
   * @return an [[Watched.Action Action]] that will determine the next step in the watch.
   */
  def handleInput(inputStream: InputStream): Watched.Action

  /**
   * This is run before each watch iteration and if it returns true, the watch is terminated.
   * @param count The current number of watch iterations.
   * @param lastStatus true if the previous task execution completed successfully
   * @return the Action to apply
   */
  def preWatch(count: Int, lastStatus: Boolean): Watched.Action

  /**
   * Callback that is invoked whenever a file system vent is detected. The next step of the watch
   * is determined by the [[Watched.Action Action]] returned by the callback.
   * @param event the detected sbt.io.FileEventMonitor.Event.
   * @return the next [[Watched.Action Action]] to run.
   */
  def onWatchEvent(event: Event[FileCacheEntry]): Watched.Action

  /**
   * Transforms the state after the watch terminates.
   * @param action the [[Watched.Action Action]] that caused the build to terminate
   * @param command the command that the watch was repeating
   * @param state the initial state prior to the start of continuous execution
   * @return the updated state.
   */
  def onWatchTerminated(action: Watched.Action, command: String, state: State): State

  /**
   * The optional message to log when a build is triggered.
   * @param typedPath the path that triggered the build
   * @param count the current iteration
   * @return an optional log message.
   */
  def triggeredMessage(typedPath: TypedPath, count: Int): Option[String]

  /**
   * The optional message to log before each watch iteration.
   * @param count the current iteration
   * @return an optional log message.
   */
  def watchingMessage(count: Int): Option[String]
}

/**
 * Provides a default implementation of [[WatchConfig]].
 */
object WatchConfig {

  /**
   *  Create an instance of [[WatchConfig]].
   * @param logger logger for watch events
   * @param fileEventMonitor the monitor for file system events.
   * @param handleInput callback that is periodically invoked to check whether to continue or
   *                    terminate the watch based on user input. It is also possible to, for
   *                    example time out the watch using this callback.
   * @param preWatch callback to invoke before waiting for updates from the sbt.io.FileEventMonitor.
   *                 The input parameters are the current iteration count and whether or not
   *                 the last invocation of the command was successful. Typical uses would be to
   *                 terminate the watch after a fixed number of iterations or to terminate the
   *                 watch if the command was unsuccessful.
   * @param onWatchEvent callback that is invoked when
   * @param onWatchTerminated callback that is invoked to update the state after the watch
   *                          terminates.
   * @param triggeredMessage optional message that will be logged when a new build is triggered.
   *                         The input parameters are the sbt.io.TypedPath that triggered the new
   *                         build and the current iteration count.
   * @param watchingMessage optional message that is printed before each watch iteration begins.
   *                        The input parameter is the current iteration count.
   * @return a [[WatchConfig]] instance.
   */
  def default(
      logger: Logger,
      fileEventMonitor: FileEventMonitor[FileCacheEntry],
      handleInput: InputStream => Watched.Action,
      preWatch: (Int, Boolean) => Watched.Action,
      onWatchEvent: Event[FileCacheEntry] => Watched.Action,
      onWatchTerminated: (Watched.Action, String, State) => State,
      triggeredMessage: (TypedPath, Int) => Option[String],
      watchingMessage: Int => Option[String]
  ): WatchConfig = {
    val l = logger
    val fem = fileEventMonitor
    val hi = handleInput
    val pw = preWatch
    val owe = onWatchEvent
    val owt = onWatchTerminated
    val tm = triggeredMessage
    val wm = watchingMessage
    new WatchConfig {
      override def logger: Logger = l
      override def fileEventMonitor: FileEventMonitor[FileCacheEntry] = fem
      override def handleInput(inputStream: InputStream): Watched.Action = hi(inputStream)
      override def preWatch(count: Int, lastResult: Boolean): Watched.Action =
        pw(count, lastResult)
      override def onWatchEvent(event: Event[FileCacheEntry]): Watched.Action = owe(event)
      override def onWatchTerminated(action: Watched.Action, command: String, state: State): State =
        owt(action, command, state)
      override def triggeredMessage(typedPath: TypedPath, count: Int): Option[String] =
        tm(typedPath, count)
      override def watchingMessage(count: Int): Option[String] = wm(count)
    }
  }
}
