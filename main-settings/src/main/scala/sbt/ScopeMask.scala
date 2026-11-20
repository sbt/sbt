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
    project: Boolean = true, // scruff: ignore(no_default_param)
    config: Boolean = true, // scruff: ignore(no_default_param)
    task: Boolean = true, // scruff: ignore(no_default_param)
    extra: Boolean = true // scruff: ignore(no_default_param)
):
  def concatShow(p: String, c: String, t: String, sep: String, x: String): String =
    val sb = new StringBuilder
    if project then
      sb.append(p); ()
    if config then
      sb.append(c); ()
    if task then
      sb.append(t); ()
    sb.append(sep)
    if extra then
      sb.append(x); ()
    sb.toString
