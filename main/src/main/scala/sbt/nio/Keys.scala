/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.nio

import java.nio.file.Path

import sbt.BuildSyntax.{ settingKey, taskKey }
import sbt.internal.util.AttributeKey
import sbt.nio.file.{ FileAttributes, FileTreeView, Glob }

object Keys {
  val allPaths = taskKey[Seq[Path]](
    "All of the file inputs for a task with no filters applied. Regular files and directories are included."
  )
  val changedFiles =
    taskKey[Seq[Path]](
      "All of the file inputs for a task that have changed since the last run. Includes new and modified files but excludes deleted files."
    )
  val modifiedFiles =
    taskKey[Seq[Path]](
      "All of the file inputs for a task that have changed since the last run. Files are considered modified based on either the last modified time or the file stamp for the file."
    )
  val removedFiles =
    taskKey[Seq[Path]]("All of the file inputs for a task that have changed since the last run.")
  val allFiles =
    taskKey[Seq[Path]]("All of the file inputs for a task excluding directories and hidden files.")
  val fileInputs = settingKey[Seq[Glob]](
    "The file globs that are used by a task. This setting will generally be scoped per task. It will also be used to determine the sources to watch during continuous execution."
  )
  val fileOutputs = taskKey[Seq[Glob]]("Describes the output files of a task.")
  val fileStamper = settingKey[FileStamper](
    "Toggles the file stamping implementation used to determine whether or not a file has been modified."
  )
  val fileTreeView =
    taskKey[FileTreeView.Nio[FileAttributes]]("A view of the local file system tree")
  private[sbt] val fileStamps =
    taskKey[Seq[(Path, FileStamp)]]("Retrieves the hashes for a set of files")
  private[sbt] type FileAttributeMap =
    java.util.HashMap[Path, FileStamp]
  private[sbt] val persistentFileAttributeMap =
    AttributeKey[FileAttributeMap]("persistent-file-attribute-map", Int.MaxValue)
  private[sbt] val allPathsAndAttributes =
    taskKey[Seq[(Path, FileAttributes)]]("Get all of the file inputs for a task")
  private[sbt] val fileAttributeMap = taskKey[FileAttributeMap](
    "Map of file stamps that may be cleared between task evaluation runs."
  )
  private[sbt] val stamper = taskKey[Path => FileStamp](
    "A function that computes a file stamp for a path. It may have the side effect of updating a cache."
  )
}
