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
import sbt.internal.io.Source
import sbt.io.*

import scala.concurrent.duration.*
import scala.util.Properties

object Watched:

  type WatchSource = Source

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

  private val defaultPollInterval: FiniteDuration = 500.milliseconds

  // @nowarn
  private[sbt] val newWatchService: () => WatchService =
    (() => createWatchService(defaultPollInterval)).label("Watched.newWatchService")
  def createWatchService(pollDelay: FiniteDuration): WatchService = {
    def closeWatch = new MacOSXWatchService()
    sys.props.get("sbt.watch.mode") match {
      case Some("polling") =>
        new PollingWatchService(pollDelay)
      case Some("nio") =>
        FileSystems.getDefault.newWatchService()
      case Some("closewatch")    => closeWatch
      case _ if Properties.isMac => closeWatch
      case _                     =>
        FileSystems.getDefault.newWatchService()
    }
  }

end Watched
