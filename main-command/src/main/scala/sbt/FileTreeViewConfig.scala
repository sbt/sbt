/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
import sbt.Watched.WatchSource
import sbt.internal.io.{ WatchServiceBackedObservable, WatchState }
import sbt.io.{ FileEventMonitor, FileTreeDataView, FileTreeView }
import sbt.util.Logger

import scala.concurrent.duration.FiniteDuration

/**
 * Configuration for viewing and monitoring the file system.
 */
final class FileTreeViewConfig private (
    val newDataView: () => FileTreeDataView[StampedFile],
    val newMonitor: (
        FileTreeDataView[StampedFile],
        Seq[WatchSource],
        Logger
    ) => FileEventMonitor[StampedFile]
)
object FileTreeViewConfig {

  /**
   * Create a new FileTreeViewConfig. This factory takes a generic parameter, T, that is bounded
   * by {{{sbt.io.FileTreeDataView[StampedFile]}}}. The reason for this is to ensure that a
   * sbt.io.FileTreeDataView that is instantiated by [[FileTreeViewConfig.newDataView]] can be
   * passed into [[FileTreeViewConfig.newMonitor]] without constraining the type of view to be
   * {{{sbt.io.FileTreeDataView[StampedFile]}}}.
   * @param newDataView create a new sbt.io.FileTreeDataView. This value may be cached in a global
   *                    attribute
   * @param newMonitor create a new sbt.io.FileEventMonitor using the sbt.io.FileTreeDataView
   *                   created by newDataView
   * @tparam T the subtype of sbt.io.FileTreeDataView that is returned by [[FileTreeViewConfig.newDataView]]
   * @return a [[FileTreeViewConfig]] instance.
   */
  def apply[T <: FileTreeDataView[StampedFile]](
      newDataView: () => T,
      newMonitor: (T, Seq[WatchSource], Logger) => FileEventMonitor[StampedFile]
  ): FileTreeViewConfig =
    new FileTreeViewConfig(
      newDataView,
      (view: FileTreeDataView[StampedFile], sources: Seq[WatchSource], logger: Logger) =>
        newMonitor(view.asInstanceOf[T], sources, logger)
    )

  /**
   * Provides a default [[FileTreeViewConfig]]. This view does not cache entries.
   * @param pollingInterval the maximum duration that the sbt.internal.io.EventMonitor will poll
   *                     the underlying sbt.io.WatchService when monitoring for file events
   * @param antiEntropy the duration of the period after a path triggers a build for which it is
   *                    quarantined from triggering another build
   * @return a [[FileTreeViewConfig]] instance.
   */
  def default(pollingInterval: FiniteDuration, antiEntropy: FiniteDuration): FileTreeViewConfig =
    FileTreeViewConfig(
      () => FileTreeView.DEFAULT.asDataView(StampedFile.converter),
      (_: FileTreeDataView[StampedFile], sources, logger) => {
        val ioLogger: sbt.io.WatchLogger = msg => logger.debug(msg.toString)
        FileEventMonitor.antiEntropy(
          new WatchServiceBackedObservable(
            WatchState.empty(Watched.createWatchService(), sources),
            pollingInterval,
            StampedFile.converter,
            closeService = true,
            ioLogger
          ),
          antiEntropy,
          ioLogger
        )
      }
    )
}
