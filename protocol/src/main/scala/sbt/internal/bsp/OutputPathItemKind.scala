/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.bsp

object OutputPathItemKind {

  /** The output path item references a normal file.  */
  val File: Int = 1

  /** The output path item references a directory. */
  val Directory: Int = 2
}
