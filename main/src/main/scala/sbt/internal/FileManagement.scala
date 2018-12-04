/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.BasicCommandStrings.ContinuousExecutePrefix
import sbt.Keys._
import sbt.internal.io.HybridPollingFileTreeRepository
import sbt.io.FileTreeDataView.{ Observable, Observer, Observers }
import sbt.io.{ FileTreeRepository, _ }
import sbt.util.Logger

import scala.concurrent.duration._

private[sbt] object FileManagement {
  private[sbt] def defaultFileTreeRepository(
      state: State,
      extracted: Extracted
  ): FileTreeRepository[FileCacheEntry] = {
    val pollingGlobs = extracted.getOpt(Keys.pollingGlobs).getOrElse(Nil)
    val remaining = state.remainingCommands.map(_.commandLine)
    // If the session is interactive or if the commands include a continuous build, then use
    // the default configuration. Otherwise, use the sbt1_2_compat config, which does not cache
    // anything, which makes it less likely to cause issues with CI.
    val interactive =
      remaining.contains("shell") || remaining.lastOption.contains("iflast shell")
    val scripted = remaining.contains("setUpScripted")
    val continuous = remaining.lastOption.exists(_.startsWith(ContinuousExecutePrefix))
    val enableCache = extracted
      .getOpt(Keys.enableGlobalCachingFileTreeRepository)
      .getOrElse(!scripted && (interactive || continuous))
    if (enableCache) {
      if (pollingGlobs.isEmpty) FileTreeRepository.default(FileCacheEntry.default)
      else FileTreeRepository.hybrid(FileCacheEntry.default, pollingGlobs: _*)
    } else {
      FileTreeRepository.legacy(
        FileCacheEntry.default,
        (_: Any) => {},
        Watched.createWatchService(extracted.getOpt(Keys.pollInterval).getOrElse(500.milliseconds))
      )
    }
  }

  private[sbt] def monitor(
      repository: FileTreeRepository[FileCacheEntry],
      antiEntropy: FiniteDuration,
      logger: Logger
  ): FileEventMonitor[FileCacheEntry] = {
    // Forwards callbacks to the repository. The close method removes all of these
    // callbacks.
    val copied: Observable[FileCacheEntry] = new Observable[FileCacheEntry] {
      private[this] val observers = new Observers[FileCacheEntry]
      val (underlying, needClose) = repository match {
        case h: HybridPollingFileTreeRepository[FileCacheEntry] =>
          (h.toPollingRepository(antiEntropy, (msg: Any) => logger.debug(msg.toString)), true)
        case r => (r, false)
      }
      private[this] val handle = underlying.addObserver(observers)
      override def addObserver(observer: Observer[FileCacheEntry]): Int =
        observers.addObserver(observer)
      override def removeObserver(handle: Int): Unit = observers.removeObserver(handle)
      override def close(): Unit = {
        underlying.removeObserver(handle)
        if (needClose) underlying.close()
      }
    }
    new FileEventMonitor[FileCacheEntry] {
      val monitor =
        FileEventMonitor.antiEntropy(
          copied,
          antiEntropy,
          new WatchLogger { override def debug(msg: => Any): Unit = logger.debug(msg.toString) },
          50.millis,
          10.minutes
        )
      override def poll(duration: Duration): Seq[FileEventMonitor.Event[FileCacheEntry]] =
        monitor.poll(duration)
      override def close(): Unit = monitor.close()
    }
  }

  private[sbt] def repo: Def.Initialize[Task[FileTreeRepository[FileCacheEntry]]] = Def.task {
    lazy val msg = s"Tried to get FileTreeRepository for uninitialized state."
    state.value.get(Keys.globalFileTreeRepository).getOrElse(throw new IllegalStateException(msg))
  }
}
