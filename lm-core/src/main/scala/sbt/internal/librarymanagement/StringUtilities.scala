/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt.internal.librarymanagement

import java.util.Locale

object StringUtilities {
  @deprecated(
    "Different use cases require different normalization.  Use Project.normalizeModuleID or normalizeProjectID instead.",
    "0.13.0"
  )
  def normalize(s: String) = s.toLowerCase(Locale.ENGLISH).replaceAll("""\W+""", "-")
  def nonEmpty(s: String, label: String): Unit =
    require(s.trim.length > 0, label + " cannot be empty.")
  def appendable(s: String) = if (s.isEmpty) "" else "_" + s
}
