/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package langserver

object DiagnosticSeverity {

  /**
   * Reports an error.
   */
  val Error = 1L

  /**
   * Reports a warning.
   */
  val Warning = 2L

  /**
   * Reports an information.
   */
  val Information = 3L

  /**
   * Reports a hint.
   */
  val Hint = 4L
}
