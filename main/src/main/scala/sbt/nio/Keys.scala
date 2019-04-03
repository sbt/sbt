/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.nio

import java.nio.file.Path

import sbt.BuildSyntax.{ settingKey, taskKey }
import sbt.nio.file.Glob

object Keys {
  val fileInputs = settingKey[Seq[Glob]](
    "The file globs that are used by a task. This setting will generally be scoped per task. It will also be used to determine the sources to watch during continuous execution."
  )
  val fileOutputs = taskKey[Seq[Glob]]("Describes the output files of a task")
  val fileHashes = taskKey[Seq[(Path, FileStamp.Hash)]]("Retrieves the hashes for a set of files")
  val fileLastModifiedTimes = taskKey[Seq[(Path, FileStamp.LastModified)]](
    "Retrieves the last modified times for a set of files"
  )
  private[sbt] val fileAttributeMap =
    taskKey[java.util.HashMap[Path, (Option[FileStamp.Hash], Option[FileStamp.LastModified])]](
      "Map of file stamps that may be cleared between task evaluation runs."
    )
}
