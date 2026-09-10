/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

private[sbt] object VcsUriFragment:

  def validate(fragment: String): Unit =
    if fragment == null then throw new IllegalArgumentException("VCS URI fragment must not be null")
    if fragment.isEmpty then
      throw new IllegalArgumentException("VCS URI fragment must not be empty")
    fragment.foreach { c =>
      if !isAllowed(c) then
        throw new IllegalArgumentException(
          "Invalid character in VCS URI fragment (only ASCII letters, digits, and - _ . / + are allowed)"
        )
    }

  private def isAllowed(c: Char): Boolean =
    (c >= 'a' && c <= 'z') ||
      (c >= 'A' && c <= 'Z') ||
      (c >= '0' && c <= '9') ||
      c == '-' || c == '_' || c == '.' || c == '/' || c == '+'
