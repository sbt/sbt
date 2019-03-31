/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.IOException

import sbt.internal.io.HybridPollingFileTreeRepository
import sbt.io.FileTreeDataView.{ Entry, Observable, Observer, Observers }
import sbt.io.{ FileTreeRepository, _ }
import sbt.util.Logger

import scala.concurrent.duration._

private[sbt] object FileManagement {
  private[sbt] def monitor(
      repository: FileTreeRepository[FileAttributes],
      antiEntropy: FiniteDuration,
      logger: Logger
  ): FileEventMonitor[FileAttributes] = {
    // Forwards callbacks to the repository. The close method removes all of these
    // callbacks.
    val copied: Observable[FileAttributes] = new Observable[FileAttributes] {
      private[this] val observers = new Observers[FileAttributes]
      val underlying = repository match {
        case h: HybridPollingFileTreeRepository[FileAttributes] =>
          h.toPollingRepository(antiEntropy, (msg: Any) => logger.debug(msg.toString))
        case r => r
      }
      private[this] val handle = underlying.addObserver(observers)
      override def addObserver(observer: Observer[FileAttributes]): Int =
        observers.addObserver(observer)
      override def removeObserver(handle: Int): Unit = observers.removeObserver(handle)
      override def close(): Unit = {
        underlying.removeObserver(handle)
        underlying.close()
      }
    }
    new FileEventMonitor[FileAttributes] {
      val monitor =
        FileEventMonitor.antiEntropy(
          copied,
          antiEntropy,
          new WatchLogger { override def debug(msg: => Any): Unit = logger.debug(msg.toString) },
          50.millis,
          10.minutes
        )
      override def poll(duration: Duration): Seq[FileEventMonitor.Event[FileAttributes]] =
        monitor.poll(duration)
      override def close(): Unit = monitor.close()
    }
  }

  private[sbt] class CopiedFileTreeRepository[T](underlying: FileTreeRepository[T])
      extends FileTreeRepository[T] {
    def addObserver(observer: Observer[T]) = underlying.addObserver(observer)
    def close(): Unit = {} // Don't close the underlying observable
    def list(glob: Glob): Seq[TypedPath] = underlying.list(glob)
    def listEntries(glob: Glob): Seq[Entry[T]] = underlying.listEntries(glob)
    def removeObserver(handle: Int): Unit = underlying.removeObserver(handle)
    def register(glob: Glob): Either[IOException, Boolean] = underlying.register(glob)
    def unregister(glob: Glob): Unit = underlying.unregister(glob)
  }
}
