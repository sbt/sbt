/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import org.apache.logging.log4j.{ Logger => XLogger }
import org.apache.logging.log4j.message.ObjectMessage
import sbt.internal.util.codec.JsonProtocol._
import sbt.util._
import scala.reflect.runtime.universe.TypeTag
import sjsonnew.JsonFormat

/**
 * Delegates log events to the associated LogExchange.
 */
class ManagedLogger(
    val name: String,
    val channelName: Option[String],
    val execId: Option[String],
    xlogger: XLogger,
    terminal: Option[Terminal]
) extends Logger {
  def this(name: String, channelName: Option[String], execId: Option[String], xlogger: XLogger) =
    this(name, channelName, execId, xlogger, None)
  override def trace(t: => Throwable): Unit =
    logEvent(Level.Error, TraceEvent("Error", t, channelName, execId))
  override def log(level: Level.Value, message: => String): Unit = {
    xlogger.log(
      ConsoleAppender.toXLevel(level),
      new ObjectMessage(StringEvent(level.toString, message, channelName, execId))
    )
  }

  // send special event for success since it's not a real log level
  override def success(message: => String): Unit = {
    if (terminal.fold(true)(_.isSuccessEnabled)) {
      infoEvent[SuccessEvent](SuccessEvent(message))(
        implicitly[JsonFormat[SuccessEvent]],
        StringTypeTag.fast[SuccessEvent],
      )
    }
  }

  @deprecated("Use macro-powered StringTypeTag.fast instead", "1.4.0")
  def registerStringCodec[A](
      s: ShowLines[A],
      tt: scala.reflect.runtime.universe.TypeTag[A]
  ): Unit = {
    LogExchange.registerStringCodec[A](s, tt)
  }
  def registerStringCodec[A: ShowLines: StringTypeTag]: Unit = {
    LogExchange.registerStringCodec[A]
  }

  @deprecated("Use macro-powered StringTypeTag.fast instead", "1.4.0")
  final def debugEvent[A](event: => A, f: JsonFormat[A], t: TypeTag[A]): Unit =
    debugEvent(event)(f, StringTypeTag.apply(t))
  @deprecated("Use macro-powered StringTypeTag.fast instead", "1.4.0")
  final def infoEvent[A](event: => A, f: JsonFormat[A], t: TypeTag[A]): Unit =
    infoEvent(event)(f, StringTypeTag.apply(t))
  @deprecated("Use macro-powered StringTypeTag.fast instead", "1.4.0")
  final def warnEvent[A](event: => A, f: JsonFormat[A], t: TypeTag[A]): Unit =
    warnEvent(event)(f, StringTypeTag.apply(t))
  @deprecated("Use macro-powered StringTypeTag.fast instead", "1.4.0")
  final def errorEvent[A](event: => A, f: JsonFormat[A], t: TypeTag[A]): Unit =
    errorEvent(event)(f, StringTypeTag.apply(t))

  final def debugEvent[A: JsonFormat: StringTypeTag](event: => A): Unit =
    logEvent(Level.Debug, event)
  final def infoEvent[A: JsonFormat: StringTypeTag](event: => A): Unit = logEvent(Level.Info, event)
  final def warnEvent[A: JsonFormat: StringTypeTag](event: => A): Unit = logEvent(Level.Warn, event)
  final def errorEvent[A: JsonFormat: StringTypeTag](event: => A): Unit =
    logEvent(Level.Error, event)
  @deprecated("Use macro-powered StringTypeTag.fast instead", "1.4.0")
  def logEvent[A](level: Level.Value, event: => A, f: JsonFormat[A], t: TypeTag[A]): Unit =
    logEvent(level, event)(f, StringTypeTag.apply(t))
  def logEvent[A: JsonFormat](level: Level.Value, event: => A)(
      implicit tag: StringTypeTag[A]
  ): Unit = {
    val v: A = event
    // println("logEvent " + tag.key)
    val entry: ObjectEvent[A] = ObjectEvent(level, v, channelName, execId, tag.key)
    xlogger.log(
      ConsoleAppender.toXLevel(level),
      new ObjectMessage(entry)
    )
  }

  @deprecated("No longer used.", "1.0.0")
  override def ansiCodesSupported = ConsoleAppender.formatEnabledInEnv
}
