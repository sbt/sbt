/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
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
