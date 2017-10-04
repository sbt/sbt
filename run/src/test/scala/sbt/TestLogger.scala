/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util._
import sbt.util._

object TestLogger {
  def apply[T](f: Logger => T): T = {
    val log = new BufferedLogger(ConsoleLogger())
    log.setLevel(Level.Debug)
    log.bufferQuietly(f(log))
  }
}
