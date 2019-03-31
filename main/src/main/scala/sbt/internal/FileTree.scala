/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ Path, WatchService => _ }

import sbt.internal.util.appmacro.MacroDefaults
import sbt.io.FileTreeDataView.Entry
import sbt.io._

import scala.collection.mutable
import scala.language.experimental.macros

object FileTree {
  private sealed trait CacheOptions
  private case object NoCache extends CacheOptions
  private case object UseCache extends CacheOptions
  private case object LogDifferences extends CacheOptions
  private def toPair(
      filter: Entry[FileAttributes] => Boolean
  )(e: Entry[FileAttributes]): Option[(Path, FileAttributes)] =
    e.value.toOption.flatMap(a => if (filter(e)) Some(e.typedPath.toPath -> a) else None)
  trait Repository extends sbt.internal.Repository[Seq, Glob, (Path, FileAttributes)]
  private[sbt] trait DynamicInputs {
    def value: Option[mutable.Set[Glob]]
  }
  private[sbt] object DynamicInputs {
    def empty: DynamicInputs = new impl(Some(mutable.Set.empty[Glob]))
    final val none: DynamicInputs = new impl(None)
    private final class impl(override val value: Option[mutable.Set[Glob]]) extends DynamicInputs
    implicit def default: DynamicInputs = macro MacroDefaults.dynamicInputs
  }
  private[sbt] object Repository {

    /**
     * Provide a default [[Repository]] that works within a task definition, e.g. Def.task. It's
     * implemented as a macro so that it can call `.value` on a TaskKey. Using a macro also allows
     * us to use classes that aren't actually available in this project, e.g. sbt.Keys.
     * @return a [[Repository]] instance
     */
    implicit def default: FileTree.Repository = macro MacroDefaults.fileTreeRepository
    private[sbt] object polling extends Repository {
      val view = FileTreeView.DEFAULT.asDataView(FileAttributes.default)
      override def get(key: Glob): Seq[(Path, FileAttributes)] =
        view.listEntries(key).flatMap(toPair(key.toEntryFilter))
      override def close(): Unit = {}
    }
  }
  private class CachingRepository(underlying: FileTreeRepository[FileAttributes])
      extends Repository {
    lazy val cacheOptions = System.getProperty("sbt.io.filecache") match {
      case "false" => NoCache
      case "true"  => UseCache
      case _       => LogDifferences
    }
    override def get(key: Glob): Seq[(Path, FileAttributes)] = {
      underlying.register(key)
      cacheOptions match {
        case LogDifferences =>
          val res = Repository.polling.get(key)
          val filter = key.toEntryFilter
          val cacheRes = underlying
            .listEntries(key)
            .flatMap(e => if (filter(e)) Some(e.typedPath.toPath) else None)
            .toSet
          val resSet = res.map(_._1).toSet
          if (cacheRes != resSet) {
            val msg = "Warning: got different files when using the internal file cache compared " +
              s"to polling the file system for key: $key.\n"
            val fileDiff = cacheRes diff resSet match {
              case d if d.nonEmpty =>
                new Exception("hmm").printStackTrace()
                s"Cache had files not found in the file system:\n${d.mkString("\n")}.\n"
              case _ => ""
            }
            val cacheDiff = resSet diff cacheRes match {
              case d if d.nonEmpty =>
                (if (fileDiff.isEmpty) "" else " ") +
                  s"File system had files not in the cache:\n${d.mkString("\n")}.\n"
              case _ => ""
            }
            val diff = fileDiff + cacheDiff
            val instructions = "Please open an issue at https://github.com/sbt/sbt. To disable " +
              "this warning, run sbt with -Dsbt.io.filecache=false"
            System.err.println(msg + diff + instructions)
          }
          res
        case UseCache =>
          underlying.listEntries(key).flatMap(toPair(key.toEntryFilter))
        case NoCache =>
          Repository.polling.get(key)
      }
    }
    override def close(): Unit = underlying.close()
  }
  private[sbt] def repository(underlying: FileTreeRepository[FileAttributes]): Repository =
    new CachingRepository(underlying)
}
