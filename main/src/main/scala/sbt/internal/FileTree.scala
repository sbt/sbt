/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ WatchService => _ }

import sbt.internal.util.appmacro.MacroDefaults
import sbt.io.FileTreeDataView.Entry
import sbt.io._

import scala.language.experimental.macros

object FileTree {
  trait Repository extends sbt.internal.Repository[Seq, Glob, Entry[FileCacheEntry]]
  object Repository {

    /**
     * Provide a default [[Repository]] that works within a task definition, e.g. Def.task. It's
     * implemented as a macro so that it can call `.value` on a TaskKey. Using a macro also allows
     * us to use classes that aren't actually available in this project, e.g. sbt.Keys.
     * @return a [[Repository]] instance
     */
    implicit def default: FileTree.Repository = macro MacroDefaults.fileTreeRepository
    private[sbt] object polling extends Repository {
      val view = FileTreeView.DEFAULT.asDataView(FileCacheEntry.default)
      override def get(key: Glob): Seq[Entry[FileCacheEntry]] = view.listEntries(key)
      override def close(): Unit = {}
    }
  }
  private class ViewRepository(underlying: FileTreeDataView[FileCacheEntry]) extends Repository {
    override def get(key: Glob): Seq[Entry[FileCacheEntry]] = underlying.listEntries(key)
    override def close(): Unit = {}
  }
  private class CachingRepository(underlying: FileTreeRepository[FileCacheEntry])
      extends Repository {
    override def get(key: Glob): Seq[Entry[FileCacheEntry]] = {
      underlying.register(key)
      underlying.listEntries(key)
    }
    override def close(): Unit = underlying.close()
  }
  private[sbt] def repository(underlying: FileTreeDataView[FileCacheEntry]): Repository =
    underlying match {
      case r: FileTreeRepository[FileCacheEntry] => new CachingRepository(r)
      case v                                     => new ViewRepository(v)
    }
}
