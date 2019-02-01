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
import sbt.io.FileTreeDataView.{ Entry, Observable, Observer, Observers }
import sbt.io._
import sbt.io.syntax._
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

  private def entryFilter(
      include: FileFilter,
      exclude: FileFilter
  ): Entry[FileCacheEntry] => Boolean = { e =>
    val tp = e.typedPath
    /*
     * The TypedPath has the isDirectory and isFile properties embedded. By overriding
     * these methods in java.io.File, FileFilters may be applied without needing to
     * stat the file (which is expensive) for isDirectory and isFile checks.
     */
    val file = new java.io.File(tp.toPath.toString) {
      override def isDirectory: Boolean = tp.isDirectory
      override def isFile: Boolean = tp.isFile
    }
    include.accept(file) && !exclude.accept(file)
  }
  private[sbt] def repo: Def.Initialize[Task[FileTreeRepository[FileCacheEntry]]] = Def.task {
    lazy val msg = s"Tried to get FileTreeRepository for uninitialized state."
    state.value.get(Keys.globalFileTreeRepository).getOrElse(throw new IllegalStateException(msg))
  }
  private[sbt] def dataView: Def.Initialize[Task[FileTreeDataView[FileCacheEntry]]] = Def.task {
    state.value
      .get(Keys.globalFileTreeRepository)
      .map(toDataView)
      .getOrElse(FileTreeView.DEFAULT.asDataView(FileCacheEntry.default))
  }
  private def toDataView(r: FileTreeRepository[FileCacheEntry]): FileTreeDataView[FileCacheEntry] =
    new FileTreeDataView[FileCacheEntry] {
      private def reg(glob: Glob): FileTreeDataView[FileCacheEntry] = { r.register(glob); r }
      override def listEntries(glob: Glob): Seq[Entry[FileCacheEntry]] = reg(glob).listEntries(glob)
      override def list(glob: Glob): Seq[TypedPath] = reg(glob).list(glob)
      override def close(): Unit = {}
    }
  private[sbt] def collectFiles(
      dirs: ScopedTaskable[Seq[File]],
      filter: ScopedTaskable[FileFilter],
      excludes: ScopedTaskable[FileFilter]
  ): Def.Initialize[Task[Seq[File]]] =
    Def.task {
      val sourceDirs = dirs.toTask.value
      val view: FileTreeDataView[FileCacheEntry] = dataView.value
      val include = filter.toTask.value
      val ex = excludes.toTask.value
      val sourceFilter: Entry[FileCacheEntry] => Boolean = entryFilter(include, ex)
      sourceDirs.flatMap { dir =>
        view
          .listEntries(dir.toPath ** AllPassFilter)
          .flatMap {
            case e if sourceFilter(e) => e.value.toOption.map(Stamped.file(e.typedPath, _))
            case _                    => None
          }
      }
    }

  private[sbt] def appendBaseSources: Seq[Def.Setting[Task[Seq[File]]]] = Seq(
    unmanagedSources := {
      val sources = unmanagedSources.value
      val include = (includeFilter in unmanagedSources).value
      val excl = (excludeFilter in unmanagedSources).value
      val baseDir = baseDirectory.value
      val r: FileTreeDataView[FileCacheEntry] = dataView.value
      if (sourcesInBase.value) {
        val filter: Entry[FileCacheEntry] => Boolean = entryFilter(include, excl)
        sources ++
          r.listEntries(baseDir * AllPassFilter)
            .flatMap {
              case e if filter(e) => e.value.toOption.map(Stamped.file(e.typedPath, _))
              case _              => None
            }
      } else sources
    }
  )
}
