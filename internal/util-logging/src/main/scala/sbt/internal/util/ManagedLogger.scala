/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import sbt.internal.util.codec.JsonProtocol._
import sbt.util._
import sjsonnew.JsonFormat
import sbt.internal.util.appmacro.StringTypeTag

private[sbt] trait MiniLogger {
  def log[T](level: Level.Value, message: ObjectEvent[T]): Unit
  def log(level: Level.Value, message: => String): Unit
}

/**
 * Delegates log events to the associated LogExchange.
 */
class ManagedLogger(
    val name: String,
    val channelName: Option[String],
    val execId: Option[String],
    xlogger: MiniLogger,
    terminal: Option[Terminal],
    private[sbt] val context: LoggerContext,
) extends Logger {
  def this(
      name: String,
      channelName: Option[String],
      execId: Option[String],
      xlogger: MiniLogger
  ) =
    this(name, channelName, execId, xlogger, None, LoggerContext.globalContext)
  override def trace(t: => Throwable): Unit =
    logEvent(Level.Error, TraceEvent("Error", t, channelName, execId))
  override def log(level: Level.Value, message: => String): Unit =
    xlogger.log(level, message)

  // send special event for success since it's not a real log level
  override def success(message: => String): Unit = {
    if (terminal.fold(true)(_.isSuccessEnabled)) {
      infoEvent[SuccessEvent](SuccessEvent(message))
    }
  }

  def registerStringCodec[A: ShowLines: StringTypeTag]: Unit = {
    LogExchange.registerStringCodec[A]
  }

  final def debugEvent[A: JsonFormat: StringTypeTag](event: => A): Unit =
    logEvent(Level.Debug, event)
  final def infoEvent[A: JsonFormat: StringTypeTag](event: => A): Unit = logEvent(Level.Info, event)
  final def warnEvent[A: JsonFormat: StringTypeTag](event: => A): Unit = logEvent(Level.Warn, event)
  final def errorEvent[A: JsonFormat: StringTypeTag](event: => A): Unit =
    logEvent(Level.Error, event)
  def logEvent[A: JsonFormat](level: Level.Value, event: => A)(using
      tag: StringTypeTag[A]
  ): Unit = {
    val v: A = event
    // println("logEvent " + tag.key)
    val entry: ObjectEvent[A] = ObjectEvent(level, v, channelName, execId, tag.key)
    xlogger.log(level, entry)
  }

  @deprecated("No longer used.", "1.0.0")
  override def ansiCodesSupported = ConsoleAppender.formatEnabledInEnv
}
