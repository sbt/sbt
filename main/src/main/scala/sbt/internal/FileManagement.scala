/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

import sbt.BasicCommandStrings.ContinuousExecutePrefix
import sbt.Keys._
import sbt.internal.io.HybridPollingFileTreeRepository
import sbt.internal.util.Util
import sbt.io.FileTreeDataView.{ Entry, Observable, Observer, Observers }
import sbt.io.{ FileTreeRepository, _ }
import sbt.util.{ Level, Logger }

import scala.collection.JavaConverters._
import scala.collection.mutable
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
    val pollInterval = extracted.getOpt(Keys.pollInterval).getOrElse(500.milliseconds)
    val watchLogger: WatchLogger = extracted.getOpt(Keys.logLevel) match {
      case Level.Debug =>
        new WatchLogger { override def debug(msg: => Any): Unit = println(s"[watch-debug] $msg") }
      case _ => new WatchLogger { override def debug(msg: => Any): Unit = {} }
    }
    if (enableCache) {
      if (pollingGlobs.isEmpty) FileTreeRepository.default(FileCacheEntry.default)
      else
        new HybridMonitoringRepository[FileCacheEntry](
          FileTreeRepository.hybrid(FileCacheEntry.default, pollingGlobs: _*),
          pollInterval,
          watchLogger
        )
    } else {
      if (Util.isWindows) new PollingFileRepository(FileCacheEntry.default)
      else {
        val service = Watched.createWatchService(pollInterval)
        FileTreeRepository.legacy(FileCacheEntry.default _, (_: Any) => {}, service)
      }
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
      val underlying = repository match {
        case h: HybridPollingFileTreeRepository[FileCacheEntry] =>
          h.toPollingRepository(antiEntropy, (msg: Any) => logger.debug(msg.toString))
        case r => r
      }
      private[this] val handle = underlying.addObserver(observers)
      override def addObserver(observer: Observer[FileCacheEntry]): Int =
        observers.addObserver(observer)
      override def removeObserver(handle: Int): Unit = observers.removeObserver(handle)
      override def close(): Unit = {
        underlying.removeObserver(handle)
        underlying.close()
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

  private[sbt] class HybridMonitoringRepository[T](
      underlying: HybridPollingFileTreeRepository[T],
      delay: FiniteDuration,
      logger: WatchLogger
  ) extends FileTreeRepository[T] {
    private val registered: mutable.Set[Glob] = ConcurrentHashMap.newKeySet[Glob].asScala
    override def listEntries(glob: Glob): Seq[Entry[T]] = underlying.listEntries(glob)
    override def list(glob: Glob): Seq[TypedPath] = underlying.list(glob)
    override def addObserver(observer: Observer[T]): Int = underlying.addObserver(observer)
    override def removeObserver(handle: Int): Unit = underlying.removeObserver(handle)
    override def close(): Unit = underlying.close()
    override def register(glob: Glob): Either[IOException, Boolean] = {
      registered.add(glob)
      underlying.register(glob)
    }
    override def unregister(glob: Glob): Unit = underlying.unregister(glob)
    private[sbt] def toMonitoringRepository: FileTreeRepository[T] = {
      val polling = underlying.toPollingRepository(delay, logger)
      registered.foreach(polling.register)
      polling
    }
  }
  private[sbt] def toMonitoringRepository[T](
      repository: FileTreeRepository[T]
  ): FileTreeRepository[T] = repository match {
    case p: PollingFileRepository[T]      => p.toMonitoringRepository
    case h: HybridMonitoringRepository[T] => h.toMonitoringRepository
    case r: FileTreeRepository[T]         => new CopiedFileRepository(r)
  }
  private class CopiedFileRepository[T](underlying: FileTreeRepository[T])
      extends FileTreeRepository[T] {
    def addObserver(observer: Observer[T]) = underlying.addObserver(observer)
    def close(): Unit = {} // Don't close the underlying observable
    def list(glob: Glob): Seq[TypedPath] = underlying.list(glob)
    def listEntries(glob: Glob): Seq[Entry[T]] = underlying.listEntries(glob)
    def removeObserver(handle: Int): Unit = underlying.removeObserver(handle)
    def register(glob: Glob): Either[IOException, Boolean] = underlying.register(glob)
    def unregister(glob: Glob): Unit = underlying.unregister(glob)
  }
  private[sbt] class PollingFileRepository[T](converter: TypedPath => T)
      extends FileTreeRepository[T] { self =>
    private val registered: mutable.Set[Glob] = ConcurrentHashMap.newKeySet[Glob].asScala
    private[this] val view = FileTreeView.DEFAULT
    private[this] val dataView = view.asDataView(converter)
    private[this] val handles: mutable.Map[FileTreeRepository[T], Int] =
      new ConcurrentHashMap[FileTreeRepository[T], Int].asScala
    private val observers: Observers[T] = new Observers
    override def addObserver(observer: Observer[T]): Int = observers.addObserver(observer)
    override def close(): Unit = {
      handles.foreach { case (repo, handle) => repo.removeObserver(handle) }
      observers.close()
    }
    override def list(glob: Glob): Seq[TypedPath] = view.list(glob)
    override def listEntries(glob: Glob): Seq[Entry[T]] = dataView.listEntries(glob)
    override def removeObserver(handle: Int): Unit = observers.removeObserver(handle)
    override def register(glob: Glob): Either[IOException, Boolean] = Right(registered.add(glob))
    override def unregister(glob: Glob): Unit = registered -= glob

    private[sbt] def toMonitoringRepository: FileTreeRepository[T] = {
      val legacy = FileTreeRepository.legacy(converter)
      registered.foreach(legacy.register)
      handles += legacy -> legacy.addObserver(observers)
      new FileTreeRepository[T] {
        override def listEntries(glob: Glob): Seq[Entry[T]] = legacy.listEntries(glob)
        override def list(glob: Glob): Seq[TypedPath] = legacy.list(glob)
        def addObserver(observer: Observer[T]): Int = legacy.addObserver(observer)
        override def removeObserver(handle: Int): Unit = legacy.removeObserver(handle)
        override def close(): Unit = legacy.close()
        override def register(glob: Glob): Either[IOException, Boolean] = {
          self.register(glob)
          legacy.register(glob)
        }
        override def unregister(glob: Glob): Unit = legacy.unregister(glob)
      }
    }
  }
}
