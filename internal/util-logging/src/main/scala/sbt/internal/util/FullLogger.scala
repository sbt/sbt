/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sbt.util.*
import com.github.ghik.silencer.silent

/** Promotes the simple Logger interface to the full AbstractLogger interface. */
class FullLogger(delegate: Logger) extends BasicLogger {
  @deprecated("No longer used.", "1.0.0")
  @silent override val ansiCodesSupported: Boolean = delegate.ansiCodesSupported

  def trace(t: => Throwable): Unit = {
    if (traceEnabled)
      delegate.trace(t)
  }
  def log(level: Level.Value, message: => String): Unit = {
    if (atLevel(level))
      delegate.log(level, message)
  }
  def success(message: => String): Unit =
    if (successEnabled)
      delegate.success(message)
  def control(event: ControlEvent.Value, message: => String): Unit =
    info(message)
  def logAll(events: Seq[LogEvent]): Unit = events.foreach(log)
}
object FullLogger {
  def apply(delegate: Logger): AbstractLogger =
    delegate match {
      case d: AbstractLogger => d
      case _                 => new FullLogger(delegate)
    }
}
