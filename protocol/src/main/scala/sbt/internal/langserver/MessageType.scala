/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package langserver

object MessageType {

  /** An error message. */
  val Error = 1L

  /** A warning message. */
  val Warning = 2L

  /** An information message. */
  val Info = 3L

  /** A log message. */
  val Log = 4L

  def fromLevelString(level: String): Long = {
    level.toLowerCase match {
      case "debug" => Log
      case "info"  => Info
      case "warn"  => Warning
      case "error" => Error
    }
  }
}
