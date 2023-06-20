/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

/** Specifies the Scope axes that should be used for an operation.  `true` indicates an axis should be used. */
final case class ScopeMask(
    project: Boolean = true,
    config: Boolean = true,
    task: Boolean = true,
    extra: Boolean = true
) {
  def concatShow(p: String, c: String, t: String, sep: String, x: String): String = {
    val sb = new StringBuilder
    if (project) {
      sb.append(p); ()
    }
    if (config) {
      sb.append(c); ()
    }
    if (task) {
      sb.append(t); ()
    }
    sb.append(sep)
    if (extra) {
      sb.append(x); ()
    }
    sb.toString
  }
}
