/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package bsp

object SourceItemKind {

  /** The source item references a normal file. */
  val File: Int = 1

  /** The source item references a directory. */
  val Directory: Int = 2
}
