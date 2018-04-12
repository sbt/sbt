/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import java.nio.file.FileSystems

import sbt.BasicCommandStrings.ClearOnFailure
import sbt.State.FailureWall
import sbt.internal.io.{ EventMonitor, Source, SourceModificationWatch, WatchState }
import sbt.internal.util.AttributeKey
import sbt.internal.util.Types.const
import sbt.io._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Properties

trait Watched {

  /** The files watched when an action is run with a preceeding ~ */
  def watchSources(s: State): Seq[Watched.WatchSource] = Nil
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
  val defaultWatchingMessage
    : WatchState => String = _.count + ". Waiting for source changes... (press enter to interrupt)"
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
    def apply(base: File): Source =
      apply(base, AllPassFilter, NothingFilter)
  }

  private[this] class AWatched extends Watched

  def multi(base: Watched, paths: Seq[Watched]): Watched =
    new AWatched {
      override def watchSources(s: State) = (base.watchSources(s) /: paths)(_ ++ _.watchSources(s))
      override def terminateWatch(key: Int): Boolean = base.terminateWatch(key)
      override val pollInterval = (base +: paths).map(_.pollInterval).min
      override val antiEntropy = (base +: paths).map(_.antiEntropy).min
      override def watchingMessage(s: WatchState) = base.watchingMessage(s)
      override def triggeredMessage(s: WatchState) = base.triggeredMessage(s)
    }
  def empty: Watched = new AWatched

  val PollDelay: FiniteDuration = 500.milliseconds
  val AntiEntropy: FiniteDuration = 40.milliseconds
  def isEnter(key: Int): Boolean = key == 10 || key == 13
  def printIfDefined(msg: String) = if (!msg.isEmpty) System.out.println(msg)

  def executeContinuously(watched: Watched, s: State, next: String, repeat: String): State = {
    @tailrec def shouldTerminate: Boolean =
      watched.terminateWatch(System.in.read()) || shouldTerminate
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
            EventMonitor(WatchState.empty(watched.watchService(), watched.watchSources(s)),
                         watched.pollInterval,
                         watched.antiEntropy,
                         shouldTerminate,
                         logger)
          )
      case Some(eventMonitor) =>
        printIfDefined(watched watchingMessage eventMonitor.state)
        val triggered = try eventMonitor.watch()
        catch {
          case e: Exception =>
            log.error(
              "Error occurred obtaining files to watch.  Terminating continuous execution...")
            State.handleException(e, s, log)
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

  val ContinuousEventMonitor =
    AttributeKey[EventMonitor]("watch event monitor",
                               "Internal: maintains watch state and monitor threads.")
  @deprecated("Superseded by ContinuousEventMonitor", "1.1.5")
  val ContinuousState =
    AttributeKey[WatchState]("watch state", "Internal: tracks state for continuous execution.")

  @deprecated("Superseded by ContinuousEventMonitor", "1.1.5")
  val ContinuousWatchService =
    AttributeKey[WatchService]("watch service",
                               "Internal: tracks watch service for continuous execution.")
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
