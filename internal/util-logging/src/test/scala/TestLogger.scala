/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sbt.util.*

object TestLogger {
  def apply[T](f: Logger => T): T = {
    val log = new BufferedLogger(ConsoleLogger())
    log.setLevel(Level.Debug)
    log.bufferQuietly(f(log))
  }
}
