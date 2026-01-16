/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sbt.internal.util.{ Appender, ManagedLogger, TraceEvent, SuccessEvent }
import sbt.internal.util.appmacro.StringTypeTag
import scala.collection.concurrent

sealed abstract class LogExchange {
  private[sbt] val stringCodecs: concurrent.Map[String, ShowLines[?]] = concurrent.TrieMap()
  private[sbt] val builtInStringCodecs: Unit = initStringCodecs()

  def logger(name: String): ManagedLogger = logger(name, None, None)
  def logger(name: String, channelName: Option[String], execId: Option[String]): ManagedLogger =
    LoggerContext.globalContext.logger(name, channelName, execId)
  def unbindLoggerAppenders(loggerName: String): Unit = {
    LoggerContext.globalContext.clearAppenders(loggerName)
  }

  def bindLoggerAppenders(
      loggerName: String,
      appenders: Seq[(Appender, Level.Value)]
  ): Unit = {
    appenders.foreach(LoggerContext.globalContext.addAppender(loggerName, _))
    ()
  }

  private[sbt] def initStringCodecs(): Unit = {
    import sbt.internal.util.codec.SuccessEventShowLines.given
    import sbt.internal.util.codec.ThrowableShowLines.given
    import sbt.internal.util.codec.TraceEventShowLines.given

    registerStringCodec[Throwable]
    registerStringCodec[TraceEvent]
    registerStringCodec[SuccessEvent]
  }

  def stringCodec[A](tag: String): Option[ShowLines[A]] =
    stringCodecs.get(tag) map { _.asInstanceOf[ShowLines[A]] }
  def hasStringCodec(tag: String): Boolean =
    stringCodecs.contains(tag)
  def getOrElseUpdateStringCodec[A](tag: String, v: ShowLines[A]): ShowLines[A] =
    stringCodecs.getOrElseUpdate(tag, v).asInstanceOf[ShowLines[A]]

  private[sbt] def registerStringCodec[A: ShowLines: StringTypeTag]: Unit = {
    val ev = implicitly[ShowLines[A]]
    val tag = implicitly[StringTypeTag[A]]
    val _ = getOrElseUpdateStringCodec(tag.key, ev)
  }
}
object LogExchange extends LogExchange
