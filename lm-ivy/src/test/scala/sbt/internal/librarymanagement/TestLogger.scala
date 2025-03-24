package sbt
package internal
package librarymanagement

import sbt.util.*
import sbt.internal.util.*

object TestLogger {
  def apply[T](f: Logger => T): T = {
    val log = new BufferedLogger(ConsoleLogger())
    log.setLevel(Level.Debug)
    log.bufferQuietly(f(log))
  }
}
