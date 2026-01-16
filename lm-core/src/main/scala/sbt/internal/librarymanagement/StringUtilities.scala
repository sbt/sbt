/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt.internal.librarymanagement

object StringUtilities {
  def nonEmpty(s: String, label: String): Unit =
    require(s.trim.length > 0, label + " cannot be empty.")
  def appendable(s: String) = if (s.isEmpty) "" else "_" + s
}
