/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ WatchService => _ }

import sbt.nio.FileStamper
import sbt.nio.file.Glob

private[sbt] final case class DynamicInput(
    glob: Glob,
    fileStamper: FileStamper,
    forceTrigger: Boolean
)
private[sbt] object DynamicInput {
  implicit object ordering extends Ordering[DynamicInput] {
    private given globOrdering: Ordering[Glob] = Glob.ordering
    private implicit object fileStamperOrdering extends Ordering[FileStamper] {
      override def compare(left: FileStamper, right: FileStamper): Int = left match {
        case FileStamper.Hash =>
          right match {
            case FileStamper.Hash => 0
            case _                => -1
          }
        case FileStamper.LastModified =>
          right match {
            case FileStamper.LastModified => 0
            case _                        => 1
          }
      }
    }
    override def compare(left: DynamicInput, right: DynamicInput): Int = {
      globOrdering.compare(left.glob, right.glob) match {
        case 0 => fileStamperOrdering.compare(left.fileStamper, right.fileStamper)
        case i => i
      }
    }
  }
}
