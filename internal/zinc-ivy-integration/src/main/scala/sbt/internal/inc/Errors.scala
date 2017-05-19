/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

class InvalidComponent(msg: String, cause: Throwable) extends RuntimeException(msg, cause) {
  def this(msg: String) = this(msg, null)
}

final class MissingScalaJar(msg: String, cause: Throwable) extends RuntimeException(msg, cause) {
  def this(msg: String) = this(msg, null)
}

object MissingScalaJar {
  def missingTemplate(missing: String): String =
    s"The $missing could not be found in your cache nor downloaded from the Internet."
  def compiler: MissingScalaJar = new MissingScalaJar(missingTemplate("Scala compiler"))
  def library: MissingScalaJar = new MissingScalaJar(missingTemplate("Scala library"))
}
