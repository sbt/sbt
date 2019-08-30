/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
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
