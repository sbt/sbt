/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sbt.util.*
import scala.annotation.nowarn

// note that setting the logging level on this logger has no effect on its behavior, only
//   on the behavior of the delegates.
class MultiLogger(delegates: List[AbstractLogger]) extends BasicLogger {
  @deprecated("No longer used.", "1.0.0")
  override lazy val ansiCodesSupported = delegates exists supported
  @nowarn private def supported = (_: AbstractLogger).ansiCodesSupported

  override def setLevel(newLevel: Level.Value): Unit = {
    super.setLevel(newLevel)
    dispatch(new SetLevel(newLevel))
  }

  override def setTrace(level: Int): Unit = {
    super.setTrace(level)
    dispatch(new SetTrace(level))
  }

  override def setSuccessEnabled(flag: Boolean): Unit = {
    super.setSuccessEnabled(flag)
    dispatch(new SetSuccess(flag))
  }

  def trace(t: => Throwable): Unit = dispatch(new Trace(t))
  def log(level: Level.Value, message: => String): Unit = dispatch(new Log(level, message))
  def success(message: => String): Unit = dispatch(new Success(message))
  def logAll(events: Seq[LogEvent]): Unit = delegates.foreach(_.logAll(events))

  def control(event: ControlEvent.Value, message: => String): Unit =
    delegates.foreach(_.control(event, message))

  private def dispatch(event: LogEvent): Unit = {
    for (d <- delegates) {
      d.log(event)
    }
  }
}
