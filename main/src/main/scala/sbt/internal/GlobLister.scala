/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.Path

import sbt.nio.file.{ FileAttributes, FileTreeView, Glob }

/**
 * Retrieve files from a repository. This should usually be an extension class for
 * sbt.io.internal.Glob (or a Traversable collection of source instances) that allows us to
 * actually retrieve the files corresponding to those sources.
 */
private[sbt] sealed trait GlobLister extends Any {

  final def all(view: FileTreeView.Nio[FileAttributes]): Seq[(Path, FileAttributes)] = {
    all(view, FileTree.DynamicInputs.empty)
  }

  /**
   * Get the sources described this `GlobLister`. The results should not return any duplicate
   * entries for each path in the result set.
   *
   * @param view the file tree view
   * @param dynamicInputs the task dynamic inputs to track for watch.
   * @return the files described by this `GlobLister`.
   */
  def all(
      implicit view: FileTreeView.Nio[FileAttributes],
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
   * Generate a GlobLister given a particular `Glob`s.
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
        implicit view: FileTreeView.Nio[FileAttributes],
        dynamicInputs: FileTree.DynamicInputs
    ): Seq[(Path, FileAttributes)] = {
      dynamicInputs.value.foreach(_ ++= globs)
      view.list(globs)
    }
  }
}
