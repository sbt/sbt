/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.nio.file.FileSystems

import sbt.internal.LabeledFunctions.*
import sbt.internal.LegacyWatched
import sbt.internal.io.{ EventMonitor, Source, WatchState }
import sbt.internal.util.Types.const
import sbt.internal.util.AttributeKey
import sbt.io.*

import scala.concurrent.duration.*
import scala.util.Properties
import scala.annotation.nowarn

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

  /** The message to show when triggered execution waits for sources to change. */
  private[sbt] def watchingMessage(s: WatchState): String = Watched.defaultWatchingMessage(s)

  /** The message to show before an action is run. */
  private[sbt] def triggeredMessage(s: WatchState): String = Watched.defaultTriggeredMessage(s)

  /** The `WatchService` to use to monitor the file system. */
  private[sbt] def watchService(): WatchService = Watched.createWatchService()
}

object Watched {

  type WatchSource = Source

  @nowarn
  def terminateWatch(key: Int): Boolean = Watched.isEnter(key)

  private def waitMessage(project: String): String =
    s"Waiting for source changes$project... (press enter to interrupt)"

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

  @nowarn
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
    ((ws: WatchState) => projectOnWatchMessage(projectId)(ws.count, projectId, Nil).get)
      .label("Watched.projectWatchingMessage")
  @deprecated("unused", "1.3.0")
  def projectOnWatchMessage(project: String): (Int, String, Seq[String]) => Option[String] = {
    (count: Int, _: String, _: Seq[String]) =>
      Some(s"$count. ${waitMessage(s" in project $project")}")
  }.label("Watched.projectOnWatchMessage")

  @deprecated("This method is not used and may be removed in a future version of sbt", "1.3.0")
  private class AWatched extends Watched

  @deprecated("This method is not used and may be removed in a future version of sbt", "1.3.0")
  def multi(base: Watched, paths: Seq[Watched]): Watched =
    new AWatched {
      override def watchSources(s: State): Seq[Watched.WatchSource] =
        paths.foldLeft(base.watchSources(s))(_ ++ _.watchSources(s))
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

  @deprecated("Use Watch.defaultStartWatch in conjunction with the watchStartMessage key", "1.3.0")
  val defaultWatchingMessage: WatchState => String =
    ((ws: WatchState) => s"${ws.count}. ${waitMessage("")} ")
      .label("Watched.projectWatchingMessage")
  @deprecated(
    "Use Watch.defaultOnTriggerMessage in conjunction with the watchTriggeredMessage key",
    "1.3.0"
  )
  val defaultTriggeredMessage: WatchState => String =
    const("").label("Watched.defaultTriggeredMessage")
  @deprecated(
    "Use Watch.clearScreenOnTrigger in conjunction with the watchTriggeredMessage key",
    "1.3.0"
  )
  val clearWhenTriggered: WatchState => String =
    const(clearScreen).label("Watched.clearWhenTriggered")
}
