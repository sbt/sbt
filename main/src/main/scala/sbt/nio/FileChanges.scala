/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.nio

import java.nio.file.Path

import sbt.nio.file.ChangedFiles

import scala.collection.immutable.VectorBuilder

private[sbt] object FileChanges {

  /**
   * Diffs two set of file stamps to determine which of the paths in the current collection have
   * been created, modified or deleted with respect to the previous collection.
   *
   * @param current the current paths and stamps
   * @param previous the previous paths and stampsj
   * @return an option containing the FileChanges in the current stamps compared to the previous
   *         stamps. If the two collections have the same elements, the option will be empty.
   */
  def apply(
      current: Seq[(Path, FileStamp)],
      previous: Seq[(Path, FileStamp)]
  ): Option[ChangedFiles] = {
    val createdBuilder = new VectorBuilder[Path]
    val deletedBuilder = new VectorBuilder[Path]
    val updatedBuilder = new VectorBuilder[Path]
    val currentMap = current.toMap
    val prevMap = previous.toMap
    current.foreach {
      case (path, currentStamp) =>
        prevMap.get(path) match {
          case Some(oldStamp) => if (oldStamp != currentStamp) updatedBuilder += path
          case None           => createdBuilder += path
        }
    }
    previous.foreach {
      case (path, _) =>
        if (currentMap.get(path).isEmpty) deletedBuilder += path
    }
    val created = createdBuilder.result()
    val deleted = deletedBuilder.result()
    val updated = updatedBuilder.result()
    if (created.isEmpty && deleted.isEmpty && updated.isEmpty) {
      None
    } else {
      val cf = ChangedFiles(created = created, deleted = deleted, updated = updated)
      Some(cf)
    }
  }
}
