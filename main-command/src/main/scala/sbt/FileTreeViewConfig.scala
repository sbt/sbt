/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
import sbt.Watched.WatchSource
import sbt.internal.FileCacheEntry
import sbt.internal.io.{ HybridPollingFileTreeRepository, WatchServiceBackedObservable, WatchState }
import sbt.io.FileTreeDataView.{ Observable, Observer }
import sbt.io._
import sbt.util.Logger

import scala.concurrent.duration._

/**
 * Configuration for viewing and monitoring the file system.
 */
final class FileTreeViewConfig private (
    val newDataView: () => FileTreeDataView[FileCacheEntry],
    val newMonitor: (
        FileTreeDataView[FileCacheEntry],
        Seq[WatchSource],
        Logger
    ) => FileEventMonitor[FileCacheEntry]
)
object FileTreeViewConfig {
  private implicit class SourceOps(val s: WatchSource) extends AnyVal {
    def toGlob: Glob = Glob(s.base, AllPassFilter, if (s.recursive) Integer.MAX_VALUE else 0)
  }

  /**
   * Create a new FileTreeViewConfig. This factory takes a generic parameter, T, that is bounded
   * by {{{sbt.io.FileTreeDataView[FileCacheEntry]}}}. The reason for this is to ensure that a
   * sbt.io.FileTreeDataView that is instantiated by [[FileTreeViewConfig.newDataView]] can be
   * passed into [[FileTreeViewConfig.newMonitor]] without constraining the type of view to be
   * {{{sbt.io.FileTreeDataView[FileCacheEntry]}}}.
   * @param newDataView create a new sbt.io.FileTreeDataView. This value may be cached in a global
   *                    attribute
   * @param newMonitor create a new sbt.io.FileEventMonitor using the sbt.io.FileTreeDataView
   *                   created by newDataView
   * @tparam T the subtype of sbt.io.FileTreeDataView that is returned by [[FileTreeViewConfig.newDataView]]
   * @return a [[FileTreeViewConfig]] instance.
   */
  def apply[T <: FileTreeDataView[FileCacheEntry]](
      newDataView: () => T,
      newMonitor: (T, Seq[WatchSource], Logger) => FileEventMonitor[FileCacheEntry]
  ): FileTreeViewConfig =
    new FileTreeViewConfig(
      newDataView,
      (view: FileTreeDataView[FileCacheEntry], sources: Seq[WatchSource], logger: Logger) =>
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
      () => FileTreeView.DEFAULT.asDataView(FileCacheEntry.default),
      (_: FileTreeDataView[FileCacheEntry], sources, logger) => {
        val ioLogger: sbt.io.WatchLogger = msg => logger.debug(msg.toString)
        FileEventMonitor.antiEntropy(
          new WatchServiceBackedObservable(
            WatchState.empty(sources.map(_.toGlob), Watched.createWatchService()),
            delay,
            FileCacheEntry.default,
            closeService = true,
            ioLogger
          ),
          antiEntropy,
          ioLogger,
          50.milliseconds,
          10.seconds
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
      () => FileTreeRepository.default(FileCacheEntry.default),
      (
          repository: FileTreeRepository[FileCacheEntry],
          sources: Seq[WatchSource],
          logger: Logger
      ) => {
        sources.view.map(_.toGlob).foreach(repository.register)
        val copied = new Observable[FileCacheEntry] {
          override def addObserver(observer: Observer[FileCacheEntry]): Int =
            repository.addObserver(observer)
          override def removeObserver(handle: Int): Unit = repository.removeObserver(handle)
          override def close(): Unit = {} // Don't close the underlying observable
        }
        FileEventMonitor.antiEntropy(
          copied,
          antiEntropy,
          msg => logger.debug(msg.toString),
          50.milliseconds,
          10.seconds
        )
      }
    )

  /**
   * Provides a default [[FileTreeViewConfig]]. When the pollingSources argument is empty, it
   * returns the same config as [[sbt.FileTreeViewConfig.default(antiEntropy:scala\.concurrent\.duration\.FiniteDuration)*]].
   * Otherwise, it returns the same config as [[polling]].
   * @param antiEntropy the duration of the period after a path triggers a build for which it is
   *                    quarantined from triggering another build
   * @param pollingInterval the frequency with which the sbt.io.FileEventMonitor polls the file
   *                        system for the paths included in pollingSources
   * @param pollingSources the sources that will not be cached in the sbt.io.FileTreeRepository and that
   *                       will be periodically polled for changes during continuous builds.
   * @return
   */
  def default(
      antiEntropy: FiniteDuration,
      pollingInterval: FiniteDuration,
      pollingSources: Seq[WatchSource]
  ): FileTreeViewConfig = {
    if (pollingSources.isEmpty) default(antiEntropy)
    else polling(antiEntropy, pollingInterval, pollingSources)
  }

  /**
   * Provides a polling [[FileTreeViewConfig]]. Unlike the view returned by newDataView in
   * [[sbt.FileTreeViewConfig.default(antiEntropy:scala\.concurrent\.duration\.FiniteDuration)*]],
   * the view returned by newDataView will not cache any portion of the file system tree that is is
   * covered by the pollingSources parameter. The monitor that is generated by newMonitor, will
   * poll these directories for changes rather than relying on file system events from the
   * operating system. Any paths that are registered with the view that are not included in the
   * pollingSources will be cached and monitored using file system events from the operating system
   * in the same way that they are in the default view.
   *
   * @param antiEntropy the duration of the period after a path triggers a build for which it is
   *                    quarantined from triggering another build
   * @param pollingInterval the frequency with which the FileEventMonitor polls the file system
   *                        for the paths included in pollingSources
   * @param pollingSources the sources that will not be cached in the sbt.io.FileTreeRepository and that
   *                       will be periodically polled for changes during continuous builds.
   * @return a [[FileTreeViewConfig]] instance.
   */
  def polling(
      antiEntropy: FiniteDuration,
      pollingInterval: FiniteDuration,
      pollingSources: Seq[WatchSource],
  ): FileTreeViewConfig = FileTreeViewConfig(
    () => FileTreeRepository.hybrid(FileCacheEntry.default, pollingSources.map(_.toGlob): _*),
    (
        repository: HybridPollingFileTreeRepository[FileCacheEntry],
        sources: Seq[WatchSource],
        logger: Logger
    ) => {
      sources.view.map(_.toGlob).foreach(repository.register)
      FileEventMonitor
        .antiEntropy(
          repository.toPollingRepository(pollingInterval, NullWatchLogger),
          antiEntropy,
          msg => logger.debug(msg.toString),
          50.milliseconds,
          10.seconds
        )
    }
  )
}
