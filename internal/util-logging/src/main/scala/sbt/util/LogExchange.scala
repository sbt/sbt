/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sbt.internal.util._
import org.apache.logging.log4j.{ LogManager => XLogManager, Level => XLevel }
import org.apache.logging.log4j.core._
import org.apache.logging.log4j.core.appender.AsyncAppender
import org.apache.logging.log4j.core.config.{ AppenderRef, LoggerConfig }
import org.apache.logging.log4j.core.layout.PatternLayout
import scala.collection.JavaConverters._
import scala.collection.concurrent
import sjsonnew.JsonFormat

// http://logging.apache.org/log4j/2.x/manual/customconfig.html
// https://logging.apache.org/log4j/2.x/log4j-core/apidocs/index.html

sealed abstract class LogExchange {
  private[sbt] lazy val context: LoggerContext = init()
  private[sbt] lazy val builtInStringCodecs: Unit = initStringCodecs()
  private[sbt] lazy val asyncStdout: AsyncAppender = buildAsyncStdout
  private[sbt] val stringCodecs: concurrent.Map[String, ShowLines[_]] = concurrent.TrieMap()

  def logger(name: String): ManagedLogger = logger(name, None, None)
  def logger(name: String, channelName: Option[String], execId: Option[String]): ManagedLogger = {
    val _ = context
    val codecs = builtInStringCodecs
    val ctx = XLogManager.getContext(false) match { case x: LoggerContext => x }
    val config = ctx.getConfiguration
    val loggerConfig = LoggerConfig.createLogger(
      false,
      XLevel.DEBUG,
      name,
      // disable the calculation of caller location as it is very expensive
      // https://issues.apache.org/jira/browse/LOG4J2-153
      "false",
      Array[AppenderRef](),
      null,
      config,
      null
    )
    config.addLogger(name, loggerConfig)
    ctx.updateLoggers
    val logger = ctx.getLogger(name)
    new ManagedLogger(name, channelName, execId, logger, Some(Terminal.get))
  }
  def unbindLoggerAppenders(loggerName: String): Unit = {
    val lc = loggerConfig(loggerName)
    lc.getAppenders.asScala foreach {
      case (k, v) => lc.removeAppender(k)
    }
  }
  def bindLoggerAppenders(loggerName: String, appenders: List[(Appender, Level.Value)]): Unit = {
    val lc = loggerConfig(loggerName)
    appenders foreach {
      case (x, lv) => lc.addAppender(x, ConsoleAppender.toXLevel(lv), null)
    }
  }
  def loggerConfig(loggerName: String): LoggerConfig = {
    val ctx = XLogManager.getContext(false) match { case x: LoggerContext => x }
    val config = ctx.getConfiguration
    config.getLoggerConfig(loggerName)
  }

  // Construct these StringTypeTags manually, because they're used at the very startup of sbt
  // and we'll try not to initialize the universe by using the StringTypeTag.apply that requires a TypeTag
  // A better long-term solution could be to make StringTypeTag.apply a macro.
  lazy val stringTypeTagThrowable = StringTypeTag[Throwable]("scala.Throwable")
  lazy val stringTypeTagTraceEvent = StringTypeTag[TraceEvent]("sbt.internal.util.TraceEvent")
  lazy val stringTypeTagSuccessEvent = StringTypeTag[SuccessEvent]("sbt.internal.util.SuccessEvent")

  private[sbt] def initStringCodecs(): Unit = {
    import sbt.internal.util.codec.ThrowableShowLines._
    import sbt.internal.util.codec.TraceEventShowLines._
    import sbt.internal.util.codec.SuccessEventShowLines._

    registerStringCodecByStringTypeTag(stringTypeTagThrowable)
    registerStringCodecByStringTypeTag(stringTypeTagTraceEvent)
    registerStringCodecByStringTypeTag(stringTypeTagSuccessEvent)
  }

  // This is a dummy layout to avoid casting error during PatternLayout.createDefaultLayout()
  // that was originally used for ConsoleAppender.
  // The stacktrace shows it's having issue initializing default DefaultConfiguration.
  // Since we currently do not use Layout inside ConsoleAppender, the actual pattern is not relevant.
  private[sbt] lazy val dummyLayout: PatternLayout = {
    val _ = context
    val ctx = XLogManager.getContext(false) match { case x: LoggerContext => x }
    val config = ctx.getConfiguration
    val lo = PatternLayout.newBuilder
      .withConfiguration(config)
      .withPattern(PatternLayout.SIMPLE_CONVERSION_PATTERN)
      .build
    lo
  }

  @deprecated("It is now necessary to provide a json format instance", "1.4.0")
  def jsonCodec[A](tag: String): Option[JsonFormat[A]] = None
  @deprecated("Always returns false", "1.4.0")
  def hasJsonCodec(tag: String): Boolean = false
  @deprecated("This is a no-op", "1.4.0")
  def getOrElseUpdateJsonCodec[A](tag: String, v: JsonFormat[A]): JsonFormat[A] = v
  @deprecated("The log manager no longer caches jsonCodecs", "1.4.0")
  def jsonCodecs(): concurrent.Map[String, JsonFormat[_]] = concurrent.TrieMap.empty

  def stringCodec[A](tag: String): Option[ShowLines[A]] =
    stringCodecs.get(tag) map { _.asInstanceOf[ShowLines[A]] }
  def hasStringCodec(tag: String): Boolean =
    stringCodecs.contains(tag)
  def getOrElseUpdateStringCodec[A](tag: String, v: ShowLines[A]): ShowLines[A] =
    stringCodecs.getOrElseUpdate(tag, v).asInstanceOf[ShowLines[A]]

  @deprecated("Prefer macro based registerStringCodec", "1.4.0")
  def registerStringCodec[A](
      st: ShowLines[A],
      tt: scala.reflect.runtime.universe.TypeTag[A]
  ): Unit = {
    registerStringCodecByStringTypeTag(StringTypeTag.apply[A](tt))(st)
  }
  private[sbt] def registerStringCodec[A: ShowLines: StringTypeTag]: Unit = {
    registerStringCodecByStringTypeTag(implicitly[StringTypeTag[A]])
  }

  private[sbt] def registerStringCodecByStringTypeTag[A: ShowLines](tag: StringTypeTag[A]): Unit = {
    val ev = implicitly[ShowLines[A]]
    val _ = getOrElseUpdateStringCodec(tag.key, ev)
  }

  private[sbt] def buildAsyncStdout: AsyncAppender = {
    val ctx = XLogManager.getContext(false) match { case x: LoggerContext => x }
    val config = ctx.getConfiguration
    val appender = ConsoleAppender("Stdout")
    // CustomConsoleAppenderImpl.createAppender("Stdout", layout, null, null)
    appender.start
    config.addAppender(appender)
    val asyncAppender: AsyncAppender = AsyncAppender
      .newBuilder()
      .setName("AsyncStdout")
      .setAppenderRefs(Array(AppenderRef.createAppenderRef("Stdout", XLevel.DEBUG, null)))
      .setBlocking(false)
      .setConfiguration(config)
      .build
    asyncAppender.start
    config.addAppender(asyncAppender)
    asyncAppender
  }
  private[sbt] def init(): LoggerContext = {
    import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
    import org.apache.logging.log4j.core.config.Configurator
    val builder = ConfigurationBuilderFactory.newConfigurationBuilder
    builder.setConfigurationName("sbt.util.logging")
    val ctx = Configurator.initialize(builder.build())
    ctx match { case x: LoggerContext => x }
  }
}
object LogExchange extends LogExchange
