/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.nio

import java.nio.file.Path

/**
 * A report on the changes of the input file dependencies or output files of a task compared to
 * some previous time. It also contains the complete list of current inputs or outputs.
 *
 * @param created the files that were not present previously. When this is non empty, it does not
 *                necessarily mean that the files were recently created. It could just indicate
 *                that there was no previous cache entry for the file stamps (
 *                see [[FileChanges#noPrevious]]).
 * @param deleted the files that have been deleted. This should be empty when no previous list of
 *                files is available.
 * @param modified the files that have been modified. This should be empty when no previous list of
 *                 files is available.
 * @param unmodified the files that have no changes. This should be empty when no previous list of
 *                   files is availab.e
 */
final case class FileChanges(
    created: Seq[Path],
    deleted: Seq[Path],
    modified: Seq[Path],
    unmodified: Seq[Path]
) {

  /**
   * Return true either if there is no previous information or
   * @return true if there are no changes.
   */
  lazy val hasChanges: Boolean = created.nonEmpty || deleted.nonEmpty || modified.nonEmpty
}

object FileChanges {

  /**
   * Creates an instance of [[FileChanges]] for a collection of files for which there were no
   * previous file stamps available.
   * @param files all of the existing files.
   * @return the [[FileChanges]] with the [[FileChanges.created]] field set to the input, `files`.
   */
  def noPrevious(files: Seq[Path]): FileChanges =
    FileChanges(created = files, deleted = Nil, modified = Nil, unmodified = Nil)

  /**
   * Creates an instance of [[FileChanges]] for a collection of files for which there were no
   * changes when compared to the previous file stamps.
   * @param files all of the existing files.
   * @return the [[FileChanges]] with the [[FileChanges.unmodified]] field set to the input, `files`.
   */
  def unmodified(files: Seq[Path]): FileChanges =
    FileChanges(created = Nil, deleted = Nil, modified = Nil, unmodified = files)
}
