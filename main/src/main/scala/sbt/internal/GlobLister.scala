/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.Path

import sbt.io.Glob

/**
 * Retrieve files from a repository. This should usually be an extension class for
 * sbt.io.internal.Glob (or a Traversable collection of source instances) that allows us to
 * actually retrieve the files corresponding to those sources.
 */
private[sbt] sealed trait GlobLister extends Any {

  /**
   * Get the sources described this [[GlobLister]].
   *
   * @param repository the [[FileTree.Repository]] to delegate file i/o.
   * @return the files described by this [[GlobLister]].
   */
  def all(implicit repository: FileTree.Repository): Seq[(Path, FileAttributes)]

  /**
   * Get the unique sources described this [[GlobLister]].
   *
   * @param repository the [[FileTree.Repository]] to delegate file i/o.
   * @return the files described by this [[GlobLister]] with any duplicates removed.
   */
  def unique(implicit repository: FileTree.Repository): Seq[(Path, FileAttributes)]
}

/**
 * Provides implicit definitions to provide a [[GlobLister]] given a Glob or
 * Traversable[Glob].
 */
object GlobLister extends GlobListers

/**
 * Provides implicit definitions to provide a [[GlobLister]] given a Glob or
 * Traversable[Glob].
 */
private[sbt] trait GlobListers {
  import GlobListers._

  /**
   * Generate a [[GlobLister]] given a particular [[Glob]]s.
   *
   * @param source the input Glob
   */
  implicit def fromGlob(source: Glob): GlobLister = new impl(source :: Nil)

  /**
   * Generate a [[GlobLister]] given a collection of Globs. If the input collection type
   * preserves uniqueness, e.g. `Set[Glob]`, then the output of [[GlobLister.all]] will be
   * the unique source list. Otherwise duplicates are possible in all and it is necessary to call
   * [[GlobLister.unique]] to de-duplicate the files.
   *
   * @param sources the collection of sources
   * @tparam T the source collection type
   */
  implicit def fromTraversableGlob[T <: Traversable[Glob]](sources: T): GlobLister =
    new impl(sources)
}
private[internal] object GlobListers {

  /**
   * Implements [[GlobLister]] given a collection of Globs. If the input collection type
   * preserves uniqueness, e.g. `Set[Glob]`, then the output will be the unique source list.
   * Otherwise duplicates are possible.
   *
   * @param globs the input globs
   * @tparam T the collection type
   */
  private class impl[T <: Traversable[Glob]](val globs: T) extends AnyVal with GlobLister {
    private def get[T0 <: Traversable[Glob]](
        traversable: T0,
        repository: FileTree.Repository
    ): Seq[(Path, FileAttributes)] =
      traversable.flatMap { glob =>
        val sourceFilter = glob.toFileFilter
        repository.get(glob).filter { case (p, _) => sourceFilter.accept(p.toFile) }
      }.toIndexedSeq

    override def all(implicit repository: FileTree.Repository): Seq[(Path, FileAttributes)] =
      get(globs, repository)
    override def unique(implicit repository: FileTree.Repository): Seq[(Path, FileAttributes)] =
      get(globs.toSet[Glob], repository)
  }
}
