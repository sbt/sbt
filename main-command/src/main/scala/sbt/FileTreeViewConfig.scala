/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
import sbt.Watched.WatchSource
import sbt.internal.io.{ WatchServiceBackedObservable, WatchState }
import sbt.io._
import FileTreeDataView.{ Observable, Observer }
import sbt.util.Logger

import scala.concurrent.duration._

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
  private implicit class RepositoryOps(val repository: FileTreeRepository[StampedFile]) {
    def register(sources: Seq[WatchSource]): Unit = sources foreach { s =>
      repository.register(s.base.toPath, if (s.recursive) Integer.MAX_VALUE else 0)
    }
  }

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
   * Provides a [[FileTreeViewConfig]] with semantics as close as possible to sbt 1.2.0. This means
   * that there is no file tree caching and the sbt.io.FileEventMonitor will use an
   * sbt.io.WatchService for monitoring the file system.
   * @param delay the maximum delay for which the background thread will poll the
   *              sbt.io.WatchService for file system events
   * @param antiEntropy the duration of the period after a path triggers a build for which it is
   *                    quarantined from triggering another build
   * @return a [[FileTreeViewConfig]] instance.
   */
  def sbt1_2_compat(
      delay: FiniteDuration,
      antiEntropy: FiniteDuration
  ): FileTreeViewConfig =
    FileTreeViewConfig(
      () => FileTreeView.DEFAULT.asDataView(StampedFile.converter),
      (_: FileTreeDataView[StampedFile], sources, logger) => {
        val ioLogger: sbt.io.WatchLogger = msg => logger.debug(msg.toString)
        FileEventMonitor.antiEntropy(
          new WatchServiceBackedObservable(
            WatchState.empty(Watched.createWatchService(), sources),
            delay,
            StampedFile.converter,
            closeService = true,
            ioLogger
          ),
          antiEntropy,
          ioLogger
        )
      }
    )

  /**
   * Provides a default [[FileTreeViewConfig]]. This view caches entries and solely relies on
   * file system events from the operating system to update its internal representation of the
   * file tree.
   * @param antiEntropy the duration of the period after a path triggers a build for which it is
   *                    quarantined from triggering another build
   * @return a [[FileTreeViewConfig]] instance.
   */
  def default(antiEntropy: FiniteDuration): FileTreeViewConfig =
    FileTreeViewConfig(
      () => FileTreeRepository.default(StampedFile.converter),
      (repository: FileTreeRepository[StampedFile], sources: Seq[WatchSource], logger: Logger) => {
        repository.register(sources)
        val copied = new Observable[StampedFile] {
          override def addObserver(observer: Observer[StampedFile]): Int =
            repository.addObserver(observer)
          override def removeObserver(handle: Int): Unit = repository.removeObserver(handle)
          override def close(): Unit = {} // Don't close the underlying observable
        }
        FileEventMonitor.antiEntropy(copied, antiEntropy, msg => logger.debug(msg.toString))
      }
    )
}
