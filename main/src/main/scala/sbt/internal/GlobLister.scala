/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListMap

import sbt.io.{ FileFilter, Glob, SimpleFileFilter }

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Retrieve files from a repository. This should usually be an extension class for
 * sbt.io.internal.Glob (or a Traversable collection of source instances) that allows us to
 * actually retrieve the files corresponding to those sources.
 */
private[sbt] sealed trait GlobLister extends Any {

  final def all(repository: FileTree.Repository): Seq[(Path, FileAttributes)] =
    all(repository, FileTree.DynamicInputs.empty)

  /**
   * Get the sources described this `GlobLister`. The results should not return any duplicate
   * entries for each path in the result set.
   *
   * @param repository the file tree repository for retrieving the files for a given glob.
   * @param dynamicInputs the task dynamic inputs to track for watch.
   * @return the files described by this `GlobLister`.
   */
  def all(
      implicit repository: FileTree.Repository,
      dynamicInputs: FileTree.DynamicInputs
  ): Seq[(Path, FileAttributes)]
}

/**
 * Provides implicit definitions to provide a `GlobLister` given a Glob or
 * Traversable[Glob].
 */
private[sbt] object GlobLister extends GlobListers

/**
 * Provides implicit definitions to provide a `GlobLister` given a Glob or
 * Traversable[Glob].
 */
private[sbt] trait GlobListers {
  import GlobListers._

  /**
   * Generate a GlobLister given a particular [[Glob]]s.
   *
   * @param source the input Glob
   */
  implicit def fromGlob(source: Glob): GlobLister = new impl(source :: Nil)

  /**
   * Generate a GlobLister given a collection of Globs.
   *
   * @param sources the collection of sources
   * @tparam T the source collection type
   */
  implicit def fromTraversableGlob[T <: Traversable[Glob]](sources: T): GlobLister =
    new impl(sources)
}
private[internal] object GlobListers {
  private def covers(left: Glob, right: Glob): Boolean = {
    right.base.startsWith(left.base) && {
      left.depth == Int.MaxValue || {
        val depth = left.base.relativize(right.base).getNameCount
        depth < left.depth - right.depth
      }
    }
  }
  private def aggregate(globs: Traversable[Glob]): Seq[(Glob, Traversable[Glob])] = {
    val sorted = globs.toSeq.sorted
    val map = new ConcurrentSkipListMap[Path, (Glob, mutable.Set[Glob])]
    if (sorted.size > 1) {
      sorted.foreach { glob =>
        map.subMap(glob.base.getRoot, glob.base.resolve(Char.MaxValue.toString)).asScala.find {
          case (_, (g, _)) => covers(g, glob)
        } match {
          case Some((_, (_, globs))) => globs += glob
          case None =>
            val globs = mutable.Set(glob)
            val filter: FileFilter = new SimpleFileFilter((file: File) => {
              globs.exists(_.toFileFilter.accept(file))
            })
            map.put(glob.base, (Glob(glob.base, filter, glob.depth), globs))
        }
      }
      map.asScala.values.toIndexedSeq
    } else sorted.map(g => g -> (g :: Nil))
  }

  /**
   * Implements `GlobLister` given a collection of Globs. If the input collection type
   * preserves uniqueness, e.g. `Set[Glob]`, then the output will be the unique source list.
   * Otherwise duplicates are possible.
   *
   * @param globs the input globs
   * @tparam T the collection type
   */
  private class impl[T <: Traversable[Glob]](val globs: T) extends AnyVal with GlobLister {
    override def all(
        implicit repository: FileTree.Repository,
        dynamicInputs: FileTree.DynamicInputs
    ): Seq[(Path, FileAttributes)] = {
      aggregate(globs).flatMap {
        case (glob, allGlobs) =>
          dynamicInputs.value.foreach(_ ++= allGlobs)
          repository.get(glob)
      }.toIndexedSeq
    }
  }
}
