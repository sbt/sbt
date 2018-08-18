/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.nio.file.FileSystems

import sbt.BasicCommandStrings._
import sbt.BasicCommands.otherCommandParser
import sbt.CommandUtil.withAttribute
import sbt.internal.io.{ EventMonitor, Source, WatchState }
import sbt.internal.util.AttributeKey
import sbt.internal.util.Types.const
import sbt.internal.util.complete.DefaultParsers
import sbt.io._
import sbt.util.{ Level, Logger }

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Properties

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
  val defaultWatchingMessage: WatchState => String = ws =>
    s"${ws.count}. Waiting for source changes... (press enter to interrupt)"

  def projectWatchingMessage(projectId: String): WatchState => String =
    ws =>
      s"${ws.count}. Waiting for source changes in project $projectId... (press enter to interrupt)"

  val defaultTriggeredMessage: WatchState => String = const("")
  val clearWhenTriggered: WatchState => String = const(clearScreen)
  def clearScreen: String = "\u001b[2J\u001b[0;0H"

  type WatchSource = Source
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
  def empty: Watched = new AWatched

  val PollDelay: FiniteDuration = 500.milliseconds
  val AntiEntropy: FiniteDuration = 40.milliseconds
  def isEnter(key: Int): Boolean = key == 10 || key == 13
  def printIfDefined(msg: String): Unit = if (!msg.isEmpty) System.out.println(msg)

  type Task = () => State
  type Setup = (State, Watched, String) => (State, Logger, Task => State)

  /**
   * Provides the '~' continuous execution command.
   * @param setup a function that provides a logger and a function from (() => State) => State.
   * @return the '~' command.
   */
  def continuous(setup: Setup): Command =
    Command(ContinuousExecutePrefix, continuousBriefHelp, continuousDetail)(otherCommandParser) {
      (state, command) =>
        Watched.command(state, command, setup)
    }

  /**
   * Implements continuous execution. It works by first parsing the command and generating a task to
   * run with each build. It can run multiple commands that are separated by ";" in the command
   * input. If any of these commands are invalid, the watch will immmediately exit.
   * @param state the initial state
   * @param command the command(s) to repeatedly apply
   * @param setup function to generate a logger and a transformation of the resultant state. The
   *              purpose of the transformation is to preserve the logging semantics that existed
   *              in the legacy version of this function in which the task would be run through
   *              MainLoop.processCommand, which is unavailable in the main-command project
   * @return the initial state if all of the input commands are valid. Otherwise, returns the
   *         initial state with the failure transformation.
   */
  private[sbt] def command(
      state: State,
      command: String,
      setup: Setup,
  ): State =
    withAttribute(state, Watched.Configuration, "Continuous execution not configured.") { w =>
      val (s0, logger, process) = setup(state, w, command)
      val s = FailureWall :: s0
      val parser = Command.combine(s.definedCommands)(s)
      val commands = command.split(";") match {
        case Array("", rest @ _*) => rest
        case Array(cmd)           => Seq(cmd)
      }
      val tasks = commands.foldLeft(Nil: Seq[Either[String, () => Either[Exception, Boolean]]]) {
        case (t, cmd) =>
          t :+ (DefaultParsers.parse(cmd, parser) match {
            case Right(task) =>
              Right { () =>
                try {
                  process(task)
                  Right(true)
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
        @tailrec def shouldTerminate: Boolean =
          (System.in.available > 0) && (w.terminateWatch(System.in.read()) || shouldTerminate)
        val watchState = WatchState.empty(w.watchService(), w.watchSources(s))
        val config = WatchConfig.default(
          logger,
          () => shouldTerminate,
          count => Some(w.triggeredMessage(watchState.withCount(count))).filter(_.nonEmpty),
          count => Some(w.watchingMessage(watchState.withCount(count))).filter(_.nonEmpty),
          watchState,
          w.pollInterval,
          w.antiEntropy
        )
        watch(task, config)
        state
      } else {
        logger.error(
          s"Terminating watch due to invalid command(s): ${invalid.mkString("'", "', '", "'")}"
        )
        state.fail
      }
    }

  private[sbt] def watch(
      task: () => Either[Exception, _],
      config: WatchConfig,
  ): Unit = {
    val eventLogger = new EventMonitor.Logger {
      override def debug(msg: => Any): Unit = config.logger.debug(msg.toString)
    }
    def debug(msg: String): Unit = if (msg.nonEmpty) config.logger.debug(msg)
    val monitor = EventMonitor(
      config.watchState,
      config.pollInterval,
      config.antiEntropy,
      config.shouldTerminate(),
      eventLogger
    )

    @tailrec
    def impl(count: Int): Unit = {
      task() match {
        case _: Right[Exception, _] =>
          config.watchingMessage(count).foreach(debug)
          if (monitor.awaitEvent()) {
            config.triggeredMessage(count).foreach(debug)
            impl(count + 1)
          }
        case Left(e) => config.logger.error(s"Terminating watch due to Unexpected error: $e")
      }
    }
    try {
      impl(count = 1)
    } finally {
      monitor.close()
      while (System.in.available() > 0) System.in.read()
    }
  }

  @deprecated("Replaced by Watched.command", "1.3.0")
  def executeContinuously(watched: Watched, s: State, next: String, repeat: String): State = {
    @tailrec def shouldTerminate: Boolean =
      (System.in.available > 0) && (watched.terminateWatch(System.in.read()) || shouldTerminate)
    val log = s.log
    val logger = new EventMonitor.Logger {
      override def debug(msg: => Any): Unit = log.debug(msg.toString)
    }
    s get ContinuousEventMonitor match {
      case None =>
        // This is the first iteration, so run the task and create a new EventMonitor
        (ClearOnFailure :: next :: FailureWall :: repeat :: s)
          .put(
            ContinuousEventMonitor,
            EventMonitor(
              WatchState.empty(watched.watchService(), watched.watchSources(s)),
              watched.pollInterval,
              watched.antiEntropy,
              shouldTerminate,
              logger
            )
          )
      case Some(eventMonitor) =>
        printIfDefined(watched watchingMessage eventMonitor.state)
        val triggered = try eventMonitor.awaitEvent()
        catch {
          case e: Exception =>
            log.error(
              "Error occurred obtaining files to watch.  Terminating continuous execution..."
            )
            s.handleError(e)
            false
        }
        if (triggered) {
          printIfDefined(watched triggeredMessage eventMonitor.state)
          ClearOnFailure :: next :: FailureWall :: repeat :: s
        } else {
          while (System.in.available() > 0) System.in.read()
          eventMonitor.close()
          s.remove(ContinuousEventMonitor)
        }
    }
  }

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
   * Returns true if the continuous execution should stop.
   * @return true if the contiuous execution should stop.
   */
  def shouldTerminate(): Boolean

  /**
   * The message to print when a build is triggered.
   * @param count the current continous iteration count
   * @return an optional string to log
   */
  def triggeredMessage(count: Int): Option[String]

  /**
   * The message to print at the beginning of each watch iteration.
   * @param count the current watch iteration
   * @return an optional string to log before each watch iteration.
   */
  def watchingMessage(count: Int): Option[String]

  /**
   * The WatchState that provides the WatchService that will be used to monitor events.
   * @return the WatchState.
   */
  def watchState: WatchState

  /**
   * The maximum duration that the EventMonitor background thread will poll the underlying
   * [[sbt.io.WatchService]] for events.
   * @return
   */
  def pollInterval: FiniteDuration

  /**
   * The period for which files that trigger a build are quarantined from triggering a new build
   * if they are modified.
   * @return the anti-entropy period.
   */
  def antiEntropy: FiniteDuration
}

/**
 * Provides a default implementation of [[WatchConfig]].
 */
object WatchConfig {

  /**
   * Generate an instance of [[WatchConfig]].
   *
   * @param logger an [[sbt.util.Logger]] instance
   * @param shouldStop returns true if the watch should stop
   * @param triggeredMessage function to generate an optional message to print when a build is

   * @param watchingMessage function to generate an optional message to print before each watch
   *                        iteration
   * @param watchState the [[WatchState]] which provides an [[sbt.io.WatchService]] to monitor
   *                   file system vents
   * @param pollInterval the maximum polling time of the [[sbt.io.WatchService]]
   * @param antiEntropy the period for which a file that triggered a build is quarantined so that
   *                    any events detected during this period do not trigger a build.
   * @return an instance of [[WatchConfig]].
   */
  def default(
      logger: Logger,
      shouldStop: () => Boolean,
      triggeredMessage: Int => Option[String],
      watchingMessage: Int => Option[String],
      watchState: WatchState,
      pollInterval: FiniteDuration,
      antiEntropy: FiniteDuration,
  ): WatchConfig = {
    val l = logger
    val ss = shouldStop
    val tm = triggeredMessage
    val wm = watchingMessage
    val ws = watchState
    val pi = pollInterval
    val ae = antiEntropy
    new WatchConfig {
      override def logger: Logger = l
      override def shouldTerminate(): Boolean = ss()
      override def triggeredMessage(count: Int): Option[String] = tm(count)
      override def watchingMessage(count: Int): Option[String] = wm(count)
      override def watchState: WatchState = ws
      override def pollInterval: FiniteDuration = pi
      override def antiEntropy: FiniteDuration = ae
    }
  }
}
